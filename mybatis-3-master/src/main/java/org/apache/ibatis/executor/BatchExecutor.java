/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 批处理的功能
 * @author Jeff Butler
 */
public class BatchExecutor extends BaseExecutor {

  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  /**
   * Statement 集合
   */
  private final List<Statement> statementList = new ArrayList<>();
  /**
   * 批处理处理结果集合
   */
  private final List<BatchResult> batchResultList = new ArrayList<>();
  /**
   * 当前 SQL
   */
  private String currentSql;
  /**
   * 当前的 MappedStatement sql 抽象信息
   */
  private MappedStatement currentStatement;

  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  /**
   * 这里，如果是同一条 未设置参数的 sql，并且与当前最新的 currentStatement 是同一个 MappedStatement
   * 则冲从 statementList 中获取 Statement，并且同样的下标从 batchResultList 中获取 BatchResult
   * 每次被调用的时候都是以handler.batch()结束，而handler.batch()对应的底层代码是调用对应的Statement的addBatch()方法
   * 它的 executeBatch()是在 doFlushStatements() 方法调用中调用的
   * JDBC demo
   *  try (Connection conn = dataSource.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql);) {
   *
   *         List<User> users = this.getUsers();
   *
   *         for (User user : users) {
   *
   *            pstmt.setString(1, user.getName());
   *
   *            pstmt.setString(2, user.getMobile());
   *
   *            pstmt.setString(3, user.getEmail());
   *
   *            pstmt.addBatch();
   *
   *         }
   *
   *         pstmt.executeBatch();
   *
   *         conn.commit();
   *
   *       }
   *
   * @param ms
   * @param parameterObject
   * @return
   * @throws SQLException
   */
  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    final Configuration configuration = ms.getConfiguration();
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    final BoundSql boundSql = handler.getBoundSql();
    final String sql = boundSql.getSql();
    final Statement stmt;
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      // 如果 currentSql 与 执行的 SQL 相等，并且 ms 是当前同一个对象
      int last = statementList.size() - 1;
      // 拿到 最后一次 statement ，这个 statement 是与当前调用 doUpdate 的是同一个执行批处理的语句
      stmt = statementList.get(last);
      // 设置超时
      applyTransactionTimeout(stmt);
      // 设置SQL 参数
      handler.parameterize(stmt);// fix Issues 322
      // 批处理结果
      // 这里记录了每一次的执行 参数
      BatchResult batchResult = batchResultList.get(last);
      // 记录批处理参数
      batchResult.addParameterObject(parameterObject);
    } else {
      // 否则，获取新的Connection 连接。并组装新的 stmt
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);    // fix Issues 322
      // 记录当前 SQL
      currentSql = sql;
      currentStatement = ms;
      statementList.add(stmt);
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    handler.batch(stmt);
    return BATCH_UPDATE_RETURN_VALUE;
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException {
    Statement stmt = null;
    try {
      // 这里在查询之前，会调用 flushStatements，其他跟 SimpleExecutor 一样，也就是说，其实只有 doUpdate 有批量的功能
      // 并且这里的一级缓存会失效
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return handler.query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    Cursor<E> cursor = handler.queryCursor(stmt);
    stmt.closeOnCompletion();
    return cursor;
  }

  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      List<BatchResult> results = new ArrayList<>();
      if (isRollback) {
        // 强制回滚，返回空列表，不处理
        return Collections.emptyList();
      }
      // 遍历每一个 statementList
      for (int i = 0, n = statementList.size(); i < n; i++) {
        Statement stmt = statementList.get(i);
        applyTransactionTimeout(stmt);
        BatchResult batchResult = batchResultList.get(i);
        // 拿到对应的 batchResult
        // 执行每个 stmt.executeBatch 并将结果设置到 setUpdateCounts 中
        try {
          batchResult.setUpdateCounts(stmt.executeBatch());
          // 拿到 MappedStatement，和参数列表
          MappedStatement ms = batchResult.getMappedStatement();
          List<Object> parameterObjects = batchResult.getParameterObjects();
          // 获取 主键生成器，然后处理主键生成
          // ??? 这里，还是有点不大明白原理，已经执行了 stmt.executeBatch()，为啥还能再这里设置主键
          // 是在 closeStatement statement.close() 的时候，才会真正执行入库的语句吗？？？
          KeyGenerator keyGenerator = ms.getKeyGenerator();
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //issue #141
            for (Object parameter : parameterObjects) {
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
          // Close statement to close cursor #1109
          closeStatement(stmt);
        } catch (BatchUpdateException e) {
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId())
              .append(" (batch index #")
              .append(i + 1)
              .append(")")
              .append(" failed.");
          if (i > 0) {
            message.append(" ")
                .append(i)
                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        results.add(batchResult);
      }
      return results;
    } finally {
      // 最终还是要关闭所有的
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }

}
