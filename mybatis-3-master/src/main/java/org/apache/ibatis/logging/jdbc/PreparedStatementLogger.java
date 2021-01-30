/**
 *    Copyright 2009-2019 the original author or authors.
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
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * PreparedStatementLogger 动态代理实现类
 *
 * PreparedStatement proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class PreparedStatementLogger extends BaseJdbcLogger implements InvocationHandler {

  /**
   * 目标代理对象
   */
  private final PreparedStatement statement;

  /**
   * 构造方法
   * @param stmt 目标对象
   * @param statementLog mybatis 适配器模式的Log
   * @param queryStack sql 栈深度
   */
  private PreparedStatementLogger(PreparedStatement stmt, Log statementLog, int queryStack) {
    super(statementLog, queryStack);
    this.statement = stmt;
  }

  /**
   * 动态代理实现类
   * @param proxy 代理对象
   * @param method 执行的方法
   * @param params 参数
   * @return 返 ResultLogger 对应的动态代理对象 | PreparedStatement 方法的返回值
   * @throws Throwable 抛出异常
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
    try {
      // 如果是Object 中声明的方法，直接调用当前直接使用当前this 执行，即可能是
      // PreparedStatementLoggerProxy.hashCode() 这些
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, params);
      }
      // execute | executeUpdate | executeQuery | addBatch
      if (EXECUTE_METHODS.contains(method.getName())) {
        // 如果开启了 debug 级别
        if (isDebugEnabled()) {
          // 输出Sql 语句的参数信息
          debug("Parameters: " + getParameterValueString(), true);
        }
        // 参数信息已经打印了，可以清空了
        clearColumnInfo();
        // 如果是 executeQuery 查询方法
        if ("executeQuery".equals(method.getName())) {
          ResultSet rs = (ResultSet) method.invoke(statement, params);
          // rs 不为空的时候， 生成 ResultSet 对应的代理类，其Invoker handler 为ResultSetLogger
          return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
        } else {
          // 如果不是 executeQuery 查询方法，则不使用动态代理返回
          // 因为
          return method.invoke(statement, params);
        }
      } else if (SET_METHODS.contains(method.getName())) {
        // 如果调用了 PreparedStatement 的set* 方法
        if ("setNull".equals(method.getName())) {
          // 如果为 setNull 方法，则添加字段信息 params[0] 为 key ，key 可以是 int | string
          setColumn(params[0], null);
        } else {
          // 否则常规操作， params[0] 为key | params[1] 为value
          setColumn(params[0], params[1]);
        }
        return method.invoke(statement, params);
      } else if ("getResultSet".equals(method.getName())) {
        // 如果是调用了 getResultSet 方法
        ResultSet rs = (ResultSet) method.invoke(statement, params);
        // rs 不为null 还是要返回代理对象滴， 只是不打印参数而已
        return rs == null ? null : ResultSetLogger.newInstance(rs, statementLog, queryStack);
      } else if ("getUpdateCount".equals(method.getName())) {
        // 获取更新返回的结构 | 目测删除的sql 也是调用 getUpdateCount
        int updateCount = (Integer) method.invoke(statement, params);
        if (updateCount != -1) {
          // 打印受影响的行数咯，同时返回 受到影响的行数
          debug("   Updates: " + updateCount, false);
        }
        return updateCount;
      } else {
        // 其他方法不返回代理对象
        return method.invoke(statement, params);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * Creates a logging version of a PreparedStatement.
   *
   * @param stmt - the statement
   * @param statementLog - the statement log
   * @param queryStack - the query stack
   * @return - the proxy
   */
  public static PreparedStatement newInstance(PreparedStatement stmt, Log statementLog, int queryStack) {
    InvocationHandler handler = new PreparedStatementLogger(stmt, statementLog, queryStack);
    ClassLoader cl = PreparedStatement.class.getClassLoader();
    // 动态代理传入的 接口，会动态生成类，并加载到jvm 中，这个类需要实现哪些接口，就是通过 newProxyInstance 传入的
    // handler 就是动态代理的处理了，最懂调用 PreparedStatement|CallableStatement 的方法，都会丢回给用户处理，而怎么处理
    // 就是 handler 中的#invoke 中
    return (PreparedStatement) Proxy.newProxyInstance(cl, new Class[]{PreparedStatement.class, CallableStatement.class}, handler);
  }

  /**
   * Return the wrapped prepared statement.
   *
   * @return the PreparedStatement
   */
  public PreparedStatement getPreparedStatement() {
    return statement;
  }

}
