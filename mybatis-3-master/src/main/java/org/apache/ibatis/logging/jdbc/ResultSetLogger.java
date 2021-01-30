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
package org.apache.ibatis.logging.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * ResultSet proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class ResultSetLogger extends BaseJdbcLogger implements InvocationHandler {

  /**
   * BLOB jdbcType 的处理类型
   * 超大长度的类型
   */
  private static final Set<Integer> BLOB_TYPES = new HashSet<>();
  /**
   * 是否为第一个 ResultSet 结果集中的第一个
   */
  private boolean first = true;
  /**
   * 统计行数
   */
  private int rows;
  /**
   * 结果集，从上一层传过来
   */
  private final ResultSet rs;
  /**
   * blob 列在ResultSet 中对应的索引
   */
  private final Set<Integer> blobColumns = new HashSet<>();

  static {
    BLOB_TYPES.add(Types.BINARY);
    BLOB_TYPES.add(Types.BLOB);
    BLOB_TYPES.add(Types.CLOB);
    BLOB_TYPES.add(Types.LONGNVARCHAR);
    BLOB_TYPES.add(Types.LONGVARBINARY);
    BLOB_TYPES.add(Types.LONGVARCHAR);
    BLOB_TYPES.add(Types.NCLOB);
    BLOB_TYPES.add(Types.VARBINARY);
  }

  private ResultSetLogger(ResultSet rs, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.rs = rs;
  }

  /**
   * 针对ResultSet.next() 方法的调用进行了一系列的后置操作
   *    通过这些后置操作会将ResultSet 数据集中的记录全部输出到日志中
   * @param proxy 代理对象，动态代理生成的对象
   * @param method 被调用的方法
   * @param params 参数
   * @return 返回值
   * @throws Throwable 异常
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    try {
      // Object 的方法，跳过
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }
      // 调用 ResultSet 的相关方法
      Object o = method.invoke(rs, params);
      // ResultSet#next()
      if ("next".equals(method.getName())) {
        // 返回结果，如果存在
        if ((Boolean) o) {
          rows++; // 行数加1
          // 是否开启了 TraceEnable 级别
          if (isTraceEnabled()) {
            // 获取元数据信息 ResultSet#getMetaData()
            ResultSetMetaData rsmd = rs.getMetaData();
            final int columnCount = rsmd.getColumnCount();
            // 获取数据集的列数，总共有多少列
            // 如果是第一行
            if (first) {
              first = false;
              // 要打印出返回的字段信息（表头）
              // 此外还会填充 blobColumns 集合，记录超大类型的列
              printColumnHeaders(rsmd, columnCount);
            }
            // 否则直接打印返回的数据行
            printColumnValues(columnCount);
          }
        } else {
          // 如果已经没有下一行了，打印总数，并将 ==== <100
          debug("     Total: " + rows, false);
        }
      }
      // 清空字段信息
      // PreparedStatementLogger 中可能残留了 字段-value 还没有清空
      // getResultSet 这个分支就可能没清空，其他情况等
      clearColumnInfo();
      return o;
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * 打印表头
   * @param rsmd ResultSet 的元数据信息
   * @param columnCount 总的列数
   * @throws SQLException 异常
   */
  private void printColumnHeaders(ResultSetMetaData rsmd, int columnCount) throws SQLException {
    // StringJoiner 工具类，字符串拼接
    // 间隔符号，前缀，后缀
    StringJoiner row = new StringJoiner(", ", "   Columns: ", "");
    for (int i = 1; i <= columnCount; i++) {
      if (BLOB_TYPES.contains(rsmd.getColumnType(i))) {
        blobColumns.add(i);
      }
      row.add(rsmd.getColumnLabel(i));
    }
    trace(row.toString(), false);
  }

  /**
   * 打印值
   * @param columnCount 列的数
   */
  private void printColumnValues(int columnCount) {
    StringJoiner row = new StringJoiner(", ", "       Row: ", "");
    for (int i = 1; i <= columnCount; i++) {
      try {
        // blog 类型不打印
        if (blobColumns.contains(i)) {
          row.add("<<BLOB>>");
        } else {
          // 都获取为 字符串类型
          row.add(rs.getString(i));
        }
      } catch (SQLException e) {
        // generally can't call getString() on a BLOB column
        row.add("<<Cannot Display>>");
      }
    }
    trace(row.toString(), false);
  }

  /**
   * Creates a logging version of a ResultSet.
   *
   * @param rs
   *          the ResultSet to proxy
   * @param statementLog
   *          the statement log
   * @param queryStack
   *          the query stack
   * @return the ResultSet with logging
   */
  public static ResultSet newInstance(ResultSet rs, Log statementLog, int queryStack) {
    InvocationHandler handler = new ResultSetLogger(rs, statementLog, queryStack);
    ClassLoader cl = ResultSet.class.getClassLoader();
    return (ResultSet) Proxy.newProxyInstance(cl, new Class[]{ResultSet.class}, handler);
  }

  /**
   * Get the wrapped result set.
   *
   * @return the resultSet
   */
  public ResultSet getRs() {
    return rs;
  }

}
