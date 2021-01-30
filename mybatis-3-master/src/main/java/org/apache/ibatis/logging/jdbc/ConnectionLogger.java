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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * 注意这里是实现了 JDK 动态代理的接口的
 *
 * 针对链接对象 Connection 的日志处理
 *
 * Connection proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class ConnectionLogger extends BaseJdbcLogger implements InvocationHandler {

  /**
   * 链接对象
   */
  private final Connection connection;

  /**
   * 私有的构造方法，在 newInstance 中才能调用
   * @param conn java.sql.Connection
   * @param statementLog mybatis 中定义的Log 标准，
   * @param queryStack sql 的层次
   */
  private ConnectionLogger(Connection conn, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.connection = conn;
  }

  /**
   * 动态代理的调用方法 InvocationHandler#invoke 的实现
   *   这里也就是说 connection 创建 PreparedStatement 时，只有
   *      prepareStatement || prepareCall 方法，并且在debug 日志等级的模式下才会输出日志
   * @param proxy 代理对象，在本实现钟用不到，哈哈，实际上很多地方往往用不到
   * @param method 被代理的方法
   * @param params 参数
   * @return 返回 PreparedStatement 对应的代理对象，或者 Connection 其他方法返回的值
   * @throws Throwable 抛出异常
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params)
      throws Throwable {
    try {
      // 如果是 Object 中的方法，直接放行
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }
      if ("prepareStatement".equals(method.getName()) || "prepareCall".equals(method.getName())) {
        // connection#prepareStatement || connection#prepareCall 的方法
        if (isDebugEnabled()) {
          // 开启了debug 模式的话；
          // 我们都知道 connection#prepareStatement 或者 connection#prepareCall
          // 都是一个参数，并且传入的是String sql ，所以这里去params 的第一个参数
          // debug ，调用 BaseJdbcLogger 类的方法，输出日志前缀
          // Preparing: + 去掉额外空格的 String sql ，然后通过 input 的参数，
          // inPut = true ，代表是输入 == >
          debug(" Preparing: " + removeExtraWhitespace((String) params[0]), true);
        }
        // 执行真正的 connection#prepareStatement || connection#prepareCall 方法，获得到 PreparedStatement
        PreparedStatement stmt = (PreparedStatement) method.invoke(connection, params);
        // 使用 PreparedStatementLogger 创建代理类的方法，得到 PreparedStatement 执行对象的动态代理
        stmt = PreparedStatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else if ("createStatement".equals(method.getName())) {
        // connection#createStatement 方法
        Statement stmt = (Statement) method.invoke(connection, params);
        // 注意了：如果是调用的 createStatement 方法，其实是生成  StatementLogger 这个代理对象的
        // 这个是没有预编译的
        stmt = StatementLogger.newInstance(stmt, statementLog, queryStack);
        return stmt;
      } else {
        // 其他方法不需要生成代理对象
        return method.invoke(connection, params);
      }
    } catch (Throwable t) {
      // 通过日志工具类进行warp 解包装
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * Creates a logging version of a connection.
   *
   * @param conn
   *          the original connection
   * @param statementLog
   *          the statement log
   * @param queryStack
   *          the query stack
   * @return the connection with logging
   */
  public static Connection newInstance(Connection conn, Log statementLog, int queryStack) {
    InvocationHandler handler = new ConnectionLogger(conn, statementLog, queryStack);
    ClassLoader cl = Connection.class.getClassLoader();
    return (Connection) Proxy.newProxyInstance(cl, new Class[]{Connection.class}, handler);
  }

  /**
   * return the wrapped connection.
   *
   * @return the connection
   */
  public Connection getConnection() {
    return connection;
  }

}
