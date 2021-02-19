/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 抽象的 StatementHandler ，具备基本骨架的实现
 * @author Clinton Begin
 */
public abstract class BaseStatementHandler implements StatementHandler {

  /**
   * 持有 configuration 对象
   */
  protected final Configuration configuration;
  /**
   * 持有对象工厂
   */
  protected final ObjectFactory objectFactory;
  /**
   * 持有类型注册对象，本质是从传递过来的 configuration 中获取
   */
  protected final TypeHandlerRegistry typeHandlerRegistry;
  /**
   * resultSetHandler 类型处理器，构造参数传递
   * 最终也是通过 configuration#newResultSetHandler 获取
   */
  protected final ResultSetHandler resultSetHandler;
  /**
   * 参数映射处理器， configuration#newParameterHandler
   */
  protected final ParameterHandler parameterHandler;

  /**
   * Executor 执行对象
   */
  protected final Executor executor;
  /**
   * SQL 抽象出来的 mappedStatement
   */
  protected final MappedStatement mappedStatement;
  /**
   * 分页参数信息
   */
  protected final RowBounds rowBounds;

  /**
   * SQL ，执行过程中的 sql
   */
  protected BoundSql boundSql;

  protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    this.configuration = mappedStatement.getConfiguration();
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;

    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();

    // 如果 boundSQL 为空，一般是写类操作。 例如： insert、update、delete ， 则先获取自增主键然后再创建 BoundSql 对象
    /*
      // SimpleExecutor.java
      @Override
      public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
          Statement stmt = null;
          try {
              Configuration configuration = ms.getConfiguration();
              // <x> 创建 StatementHandler 对象
              StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
              // 初始化 StatementHandler 对象
              stmt = prepareStatement(handler, ms.getStatementLog());
              // 执行 StatementHandler ，进行写操作
              return handler.update(stmt);
          } finally {
              // 关闭 StatementHandler 对象
              closeStatement(stmt);
          }
      }
     */
    if (boundSql == null) { // issue #435, get the key before calculating the statement
      generateKeys(parameterObject);
      boundSql = mappedStatement.getBoundSql(parameterObject);
    }

    this.boundSql = boundSql;

    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
  }

  @Override
  public BoundSql getBoundSql() {
    return boundSql;
  }

  @Override
  public ParameterHandler getParameterHandler() {
    return parameterHandler;
  }

  /**
   * 创建预编译的 Statement 对象
   * @param connection
   * @param transactionTimeout
   * @return
   * @throws SQLException
   */
  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      // 留给子类扩展
      statement = instantiateStatement(connection);
      // 设置 statement 超时
      setStatementTimeout(statement, transactionTimeout);
      // 设置 fetchSize
      setFetchSize(statement);
      return statement;
    } catch (SQLException e) {
      closeStatement(statement);
      throw e;
    } catch (Exception e) {
      closeStatement(statement);
      throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
    }
  }

  protected abstract Statement instantiateStatement(Connection connection) throws SQLException;

  protected void setStatementTimeout(Statement stmt, Integer transactionTimeout) throws SQLException {
    Integer queryTimeout = null;
    if (mappedStatement.getTimeout() != null) {
      queryTimeout = mappedStatement.getTimeout();
    } else if (configuration.getDefaultStatementTimeout() != null) {
      queryTimeout = configuration.getDefaultStatementTimeout();
    }
    if (queryTimeout != null) {
      // 设置查询超时
      stmt.setQueryTimeout(queryTimeout);
    }
    // 设置事务超时，这里如果 事务超时 为 null 并且 queryTimeout 不为null、0 and queryTimeout > transactionTimeout ，则不会已事务配置的为主
    StatementUtil.applyTransactionTimeout(stmt, queryTimeout, transactionTimeout);
  }

  protected void setFetchSize(Statement stmt) throws SQLException {
    Integer fetchSize = mappedStatement.getFetchSize();
    if (fetchSize != null) {
      stmt.setFetchSize(fetchSize);
      return;
    }
    Integer defaultFetchSize = configuration.getDefaultFetchSize();
    if (defaultFetchSize != null) {
      stmt.setFetchSize(defaultFetchSize);
    }
  }

  protected void closeStatement(Statement statement) {
    try {
      if (statement != null) {
        statement.close();
      }
    } catch (SQLException e) {
      //ignore
    }
  }

  protected void generateKeys(Object parameter) {
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    ErrorContext.instance().store();
    keyGenerator.processBefore(executor, mappedStatement, null, parameter);
    ErrorContext.instance().recall();
  }

}
