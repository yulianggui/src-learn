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
package org.apache.ibatis.datasource.pooled;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * UnpooledDataSource 的包装类
 * This is a simple, synchronous, thread-safe database connection pool.
 *
 * @author Clinton Begin
 */
public class PooledDataSource implements DataSource {

  private static final Log log = LogFactory.getLog(PooledDataSource.class);

  private final PoolState state = new PoolState(this);

  private final UnpooledDataSource dataSource;

  // OPTIONAL CONFIGURATION FIELDS
  /**
   * 可选的配置项
   */
  /**
   * 最大活跃线程，即线程池最大值
   */
  protected int poolMaximumActiveConnections = 10;
  /**
   * 空闲时间的最大连接数量
   */
  protected int poolMaximumIdleConnections = 5;
  /**
   * CheckoutTime表示应用从连接池中取出连接数，到归还连接着端时长
   * 拿出连接池之后，到归还的时间间隔，这个间隔的最大值，超过表示超时了
   */
  protected int poolMaximumCheckoutTime = 20000;
  /**
   * 等待连接池超时时间
   */
  protected int poolTimeToWait = 20000;
  /**
   * 允许的最大不可用连接的数量
   */
  protected int poolMaximumLocalBadConnectionTolerance = 3;
  /**
   * ping 查询语句，默认是没有设置
   */
  protected String poolPingQuery = "NO PING QUERY SET";
  /**
   * 是否启用 ping 的功能
   */
  protected boolean poolPingEnabled;
  /**
   * 当连接超过 该值设定的毫秒未使用时，会发送一次测试SQL 语句
   */
  protected int poolPingConnectionsNotUsedFor;

  /**
   * 用URL、用户名和密码计算出来的hash 值，用来标识该连接所在的连接池
   */
  private int expectedConnectionTypeCode;

  public PooledDataSource() {
    dataSource = new UnpooledDataSource();
  }

  public PooledDataSource(UnpooledDataSource dataSource) {
    this.dataSource = dataSource;
  }

  public PooledDataSource(String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, String username, String password) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, username, password);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  public PooledDataSource(ClassLoader driverClassLoader, String driver, String url, Properties driverProperties) {
    dataSource = new UnpooledDataSource(driverClassLoader, driver, url, driverProperties);
    expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
  }

  @Override
  public Connection getConnection() throws SQLException {
    return popConnection(dataSource.getUsername(), dataSource.getPassword()).getProxyConnection();
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return popConnection(username, password).getProxyConnection();
  }

  @Override
  public void setLoginTimeout(int loginTimeout) {
    DriverManager.setLoginTimeout(loginTimeout);
  }

  @Override
  public int getLoginTimeout() {
    return DriverManager.getLoginTimeout();
  }

  @Override
  public void setLogWriter(PrintWriter logWriter) {
    DriverManager.setLogWriter(logWriter);
  }

  @Override
  public PrintWriter getLogWriter() {
    return DriverManager.getLogWriter();
  }

  public void setDriver(String driver) {
    dataSource.setDriver(driver);
    forceCloseAll();
  }

  public void setUrl(String url) {
    dataSource.setUrl(url);
    forceCloseAll();
  }

  public void setUsername(String username) {
    dataSource.setUsername(username);
    forceCloseAll();
  }

  public void setPassword(String password) {
    dataSource.setPassword(password);
    forceCloseAll();
  }

  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    dataSource.setAutoCommit(defaultAutoCommit);
    forceCloseAll();
  }

  public void setDefaultTransactionIsolationLevel(Integer defaultTransactionIsolationLevel) {
    dataSource.setDefaultTransactionIsolationLevel(defaultTransactionIsolationLevel);
    forceCloseAll();
  }

  public void setDriverProperties(Properties driverProps) {
    dataSource.setDriverProperties(driverProps);
    forceCloseAll();
  }

  /**
   * Sets the default network timeout value to wait for the database operation to complete. See {@link Connection#setNetworkTimeout(java.util.concurrent.Executor, int)}
   *
   * @param milliseconds
   *          The time in milliseconds to wait for the database operation to complete.
   * @since 3.5.2
   */
  public void setDefaultNetworkTimeout(Integer milliseconds) {
    dataSource.setDefaultNetworkTimeout(milliseconds);
    forceCloseAll();
  }

  /**
   * The maximum number of active connections.
   *
   * @param poolMaximumActiveConnections
   *          The maximum number of active connections
   */
  public void setPoolMaximumActiveConnections(int poolMaximumActiveConnections) {
    this.poolMaximumActiveConnections = poolMaximumActiveConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of idle connections.
   *
   * @param poolMaximumIdleConnections
   *          The maximum number of idle connections
   */
  public void setPoolMaximumIdleConnections(int poolMaximumIdleConnections) {
    this.poolMaximumIdleConnections = poolMaximumIdleConnections;
    forceCloseAll();
  }

  /**
   * The maximum number of tolerance for bad connection happens in one thread
   * which are applying for new {@link PooledConnection}.
   *
   * @param poolMaximumLocalBadConnectionTolerance
   *          max tolerance for bad connection happens in one thread
   *
   * @since 3.4.5
   */
  public void setPoolMaximumLocalBadConnectionTolerance(
      int poolMaximumLocalBadConnectionTolerance) {
    this.poolMaximumLocalBadConnectionTolerance = poolMaximumLocalBadConnectionTolerance;
  }

  /**
   * The maximum time a connection can be used before it *may* be
   * given away again.
   *
   * @param poolMaximumCheckoutTime
   *          The maximum time
   */
  public void setPoolMaximumCheckoutTime(int poolMaximumCheckoutTime) {
    this.poolMaximumCheckoutTime = poolMaximumCheckoutTime;
    forceCloseAll();
  }

  /**
   * The time to wait before retrying to get a connection.
   *
   * @param poolTimeToWait
   *          The time to wait
   */
  public void setPoolTimeToWait(int poolTimeToWait) {
    this.poolTimeToWait = poolTimeToWait;
    forceCloseAll();
  }

  /**
   * The query to be used to check a connection.
   *
   * @param poolPingQuery
   *          The query
   */
  public void setPoolPingQuery(String poolPingQuery) {
    this.poolPingQuery = poolPingQuery;
    forceCloseAll();
  }

  /**
   * Determines if the ping query should be used.
   *
   * @param poolPingEnabled
   *          True if we need to check a connection before using it
   */
  public void setPoolPingEnabled(boolean poolPingEnabled) {
    this.poolPingEnabled = poolPingEnabled;
    forceCloseAll();
  }

  /**
   * If a connection has not been used in this many milliseconds, ping the
   * database to make sure the connection is still good.
   *
   * @param milliseconds
   *          the number of milliseconds of inactivity that will trigger a ping
   */
  public void setPoolPingConnectionsNotUsedFor(int milliseconds) {
    this.poolPingConnectionsNotUsedFor = milliseconds;
    forceCloseAll();
  }

  public String getDriver() {
    return dataSource.getDriver();
  }

  public String getUrl() {
    return dataSource.getUrl();
  }

  public String getUsername() {
    return dataSource.getUsername();
  }

  public String getPassword() {
    return dataSource.getPassword();
  }

  public boolean isAutoCommit() {
    return dataSource.isAutoCommit();
  }

  public Integer getDefaultTransactionIsolationLevel() {
    return dataSource.getDefaultTransactionIsolationLevel();
  }

  public Properties getDriverProperties() {
    return dataSource.getDriverProperties();
  }

  /**
   * Gets the default network timeout.
   *
   * @return the default network timeout
   * @since 3.5.2
   */
  public Integer getDefaultNetworkTimeout() {
    return dataSource.getDefaultNetworkTimeout();
  }

  public int getPoolMaximumActiveConnections() {
    return poolMaximumActiveConnections;
  }

  public int getPoolMaximumIdleConnections() {
    return poolMaximumIdleConnections;
  }

  public int getPoolMaximumLocalBadConnectionTolerance() {
    return poolMaximumLocalBadConnectionTolerance;
  }

  public int getPoolMaximumCheckoutTime() {
    return poolMaximumCheckoutTime;
  }

  public int getPoolTimeToWait() {
    return poolTimeToWait;
  }

  public String getPoolPingQuery() {
    return poolPingQuery;
  }

  public boolean isPoolPingEnabled() {
    return poolPingEnabled;
  }

  public int getPoolPingConnectionsNotUsedFor() {
    return poolPingConnectionsNotUsedFor;
  }

  /**
   * 关闭所有活跃和空闲的连接
   * Closes all active and idle connections in the pool.
   */
  public void forceCloseAll() {
    // 上锁
    synchronized (state) {
      // 组装，生产TypeCode
      expectedConnectionTypeCode = assembleConnectionTypeCode(dataSource.getUrl(), dataSource.getUsername(), dataSource.getPassword());
      // 活跃连接
      for (int i = state.activeConnections.size(); i > 0; i--) {
        try {
          // 删除弹出
          PooledConnection conn = state.activeConnections.remove(i - 1);
          // 置为无效，此时拿到连接池外边的连接，已经不可用
          conn.invalidate();
          // 得到真实的连接
          Connection realConn = conn.getRealConnection();
          // 如果没有设置自动提交。 回滚掉
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          // 关闭连接
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
      // 空闲连接
      for (int i = state.idleConnections.size(); i > 0; i--) {
        try {
          PooledConnection conn = state.idleConnections.remove(i - 1);
          conn.invalidate();

          Connection realConn = conn.getRealConnection();
          if (!realConn.getAutoCommit()) {
            realConn.rollback();
          }
          realConn.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("PooledDataSource forcefully closed/removed all connections.");
    }
  }

  public PoolState getPoolState() {
    return state;
  }

  private int assembleConnectionTypeCode(String url, String username, String password) {
    return ("" + url + username + password).hashCode();
  }

  /**
   * 压入一个链接
   * @param conn 连接
   * @throws SQLException 异常
   */
  protected void pushConnection(PooledConnection conn) throws SQLException {

    synchronized (state) {
      /**
       * 如果连接已经存在活跃连接List 中，删除掉
       * 这个逻辑是从数据库链接池的外部归还连接，在未归还之前，连接池是活跃状态，即放在 state 的 activeConnections 中de
       * 现在归还到链接池，说明这个连接应该从 活跃状态回到 空闲状态，或者直接关闭（空闲连接到达了最大）
       */
      state.activeConnections.remove(conn);
      // 当前连接是否有效
      // valid && realConnection != null && dataSource.pingConnection(this)
      // 如果 valid 为 false ，则会去发送心跳核查
      if (conn.isValid()) {
        // 1、当前的空闲连接数 < 小于最大空闲连接数
        // 2、是同一个连接池，conn 所属的连接池时与当前的连接池为同一个
        // 进入到第一个if 分支
        if (state.idleConnections.size() < poolMaximumIdleConnections && conn.getConnectionTypeCode() == expectedConnectionTypeCode) {
          // 连接使用时间累加
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // mybatis 并不会提交事务处理，将会默认回滚，即 autoCommit 为 false 时，如果为 true，则交由用户手动回滚
          // 回滚未提交的事务。 -- rollback 要在 commit 之前执行才能回滚
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 创建一个新的连接，还是使用当前的连接，进行包装
          PooledConnection newConn = new PooledConnection(conn.getRealConnection(), this);
          // 空闲连接+1
          state.idleConnections.add(newConn);
          // 重置连接创建时间戳、最后使用的时间戳从上一个 conn 传递过来的
          newConn.setCreatedTimestamp(conn.getCreatedTimestamp());
          newConn.setLastUsedTimestamp(conn.getLastUsedTimestamp());
          // 将连接置位 逻辑无效
          // 除非再次从线程池中拿出
          conn.invalidate();
          if (log.isDebugEnabled()) {
            log.debug("Returned connection " + newConn.getRealHashCode() + " to pool.");
          }
          // 通知其他线程，现在有空闲的连接了，如果是阻塞的，可以醒来尝试获取空闲连接了
          state.notifyAll();
        } else {
          // 如果空闲时间满了
          // 连接使用时间累加
          state.accumulatedCheckoutTime += conn.getCheckoutTime();
          // mybatis 并不会提交事务处理，将会默认回滚，即 autoCommit 为 false 时，如果为 true，则交由用户手动回滚
          // 回滚未提交的事务。 -- rollback 要在 commit 之前执行才能回滚
          if (!conn.getRealConnection().getAutoCommit()) {
            conn.getRealConnection().rollback();
          }
          // 真正的关闭连接
          conn.getRealConnection().close();
          if (log.isDebugEnabled()) {
            log.debug("Closed connection " + conn.getRealHashCode() + ".");
          }
          // 将连接设置为无效的连接，已经回到线程池了，外部就不能乱用了
          conn.invalidate();
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("A bad connection (" + conn.getRealHashCode() + ") attempted to return to the pool, discarding connection.");
        }
        state.badConnectionCount++;
      }
    }
  }

  /**
   * 从连接池中拿出来一个链接
   * @param username 用户名  这里的账号密码主要是用来 assembleConnectionTypeCode 这个方法的，很奇怪的设计
   * @param password 密码
   * @return 返回信息
   * @throws SQLException 异常
   */
  private PooledConnection popConnection(String username, String password) throws SQLException {
    // 是否需要等待有空闲连接，如果此时空闲连接中没有了，则需要等待
    boolean countedWait = false;
    // 获取到的连接信息
    PooledConnection conn = null;
    // 当前开始时间
    long t = System.currentTimeMillis();
    // 本次获取连接时遇到坏的连接此时，回来跳出循环的其中一种逻辑
    int localBadConnectionCount = 0;

    /**
     * 循环获取
     */
    while (conn == null) {
      synchronized (state) {
        if (!state.idleConnections.isEmpty()) {
          // 空闲连接不为空
          // Pool has available connection
          // 弹出第一个
          conn = state.idleConnections.remove(0);
          if (log.isDebugEnabled()) {
            log.debug("Checked out connection " + conn.getRealHashCode() + " from pool.");
          }
        } else {
          // Pool does not have available connection
          // 空闲连接为空
          if (state.activeConnections.size() < poolMaximumActiveConnections) {
            // Can create new connection
            // 当前最大活跃的连接数 < 配置的最多活跃线程数量，直接创建一个线程了
            conn = new PooledConnection(dataSource.getConnection(), this);
            if (log.isDebugEnabled()) {
              log.debug("Created connection " + conn.getRealHashCode() + ".");
            }
          } else {
            // Cannot create new connection
            // 不能创建新的conn 了。从 活跃的线程list 中取出最先放进去的连接
            PooledConnection oldestActiveConnection = state.activeConnections.get(0);
            // 计算是否已经超时了
            long longestCheckoutTime = oldestActiveConnection.getCheckoutTime();
            // 如果已经超时了
            if (longestCheckoutTime > poolMaximumCheckoutTime) {
              // Can claim overdue connection
              // 指标添加
              state.claimedOverdueConnectionCount++;
              state.accumulatedCheckoutTimeOfOverdueConnections += longestCheckoutTime;
              state.accumulatedCheckoutTime += longestCheckoutTime;
              // 从list 中移除掉当前这个连接
              state.activeConnections.remove(oldestActiveConnection);
              if (!oldestActiveConnection.getRealConnection().getAutoCommit()) {
                try {
                  // 尝试回滚啊
                  oldestActiveConnection.getRealConnection().rollback();
                } catch (SQLException e) {
                  /*
                     Just log a message for debug and continue to execute the following
                     statement like nothing happened.
                     Wrap the bad connection with a new PooledConnection, this will help
                     to not interrupt current executing thread and give current thread a
                     chance to join the next competition for another valid/good database
                     connection. At the end of this loop, bad {@link @conn} will be set as null.
                   */
                  log.debug("Bad connection. Could not roll back");
                }
              }
              // 新建啊
              conn = new PooledConnection(oldestActiveConnection.getRealConnection(), this);
              conn.setCreatedTimestamp(oldestActiveConnection.getCreatedTimestamp());
              conn.setLastUsedTimestamp(oldestActiveConnection.getLastUsedTimestamp());
              // 置为无效啊
              oldestActiveConnection.invalidate();
              if (log.isDebugEnabled()) {
                log.debug("Claimed overdue connection " + conn.getRealHashCode() + ".");
              }
            } else {
              // Must wait
              // 最先放进去的还没有超时
              try {
                if (!countedWait) {
                  state.hadToWaitCount++;
                  countedWait = true;
                }
                if (log.isDebugEnabled()) {
                  log.debug("Waiting as long as " + poolTimeToWait + " milliseconds for connection.");
                }
                // 当前时间--- 等待时间
                long wt = System.currentTimeMillis();
                // 设置当前线程的等待时间，进行阻塞等待
                state.wait(poolTimeToWait);
                // 计算等待时间
                state.accumulatedWaitTime += System.currentTimeMillis() - wt;
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }

        // 如果获取得到连接，则需要进行一下参数设置，如果没有获取到
        // || 线程被唤醒了 ，如果没有抢到，进行下一轮的争抢
        if (conn != null) {
          // ping to server and check the connection is valid or not
          // 核查是否有效，
          // valid && realConnection != null && dataSource.pingConnection(this)
          // 如果 valid 为 false ，则会去发送心跳核查
          if (conn.isValid()) {
            if (!conn.getRealConnection().getAutoCommit()) {
              conn.getRealConnection().rollback();
            }
            conn.setConnectionTypeCode(assembleConnectionTypeCode(dataSource.getUrl(), username, password));
            conn.setCheckoutTimestamp(System.currentTimeMillis());
            conn.setLastUsedTimestamp(System.currentTimeMillis());
            state.activeConnections.add(conn);
            state.requestCount++;
            // 获取连接池的总时长累加
            state.accumulatedRequestTime += System.currentTimeMillis() - t;
          } else {
            // 连接无效
            if (log.isDebugEnabled()) {
              log.debug("A bad connection (" + conn.getRealHashCode() + ") was returned from the pool, getting another connection.");
            }
            // 坏的连接
            state.badConnectionCount++;
            // 本次获取连接时遇到坏的连接此时，回来跳出循环的其中一种逻辑
            localBadConnectionCount++;
            // conn 置null
            conn = null;
            // 本次线程获取连接池连接信息，获取到坏连接的次数 > 最大空闲连接数 + 配置的池最大连接数，抛出异常，本次获取连接结束了
            if (localBadConnectionCount > (poolMaximumIdleConnections + poolMaximumLocalBadConnectionTolerance)) {
              if (log.isDebugEnabled()) {
                log.debug("PooledDataSource: Could not get a good connection to the database.");
              }
              throw new SQLException("PooledDataSource: Could not get a good connection to the database.");
            }
          }
        }
      }

    }

    if (conn == null) {
      if (log.isDebugEnabled()) {
        log.debug("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
      }
      throw new SQLException("PooledDataSource: Unknown severe error condition.  The connection pool returned a null connection.");
    }

    return conn;
  }

  /**
   * 核查conn 是否有效
   * Method to check to see if a connection is still usable
   *
   * @param conn
   *          - the connection to check
   * @return True if the connection is still usable
   */
  protected boolean pingConnection(PooledConnection conn) {
    boolean result = true;

    try {
      // 核查状态，是否关闭
      result = !conn.getRealConnection().isClosed();
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
      }
      result = false;
    }

    if (result && poolPingEnabled && poolPingConnectionsNotUsedFor >= 0
        && conn.getTimeElapsedSinceLastUse() > poolPingConnectionsNotUsedFor) {
      // 1、未关闭
      // 2、启用了ping 功能
      // 3、poolPingConnectionsNotUsedFor 设置了
      // 4、上一次使用连接的时间与当前的时间间隔比间隔 ping 的时间大
      try {
        if (log.isDebugEnabled()) {
          log.debug("Testing connection " + conn.getRealHashCode() + " ...");
        }
        Connection realConn = conn.getRealConnection();
        try (Statement statement = realConn.createStatement()) {
          statement.executeQuery(poolPingQuery).close();
        }
        if (!realConn.getAutoCommit()) {
          realConn.rollback();
        }
        result = true;
        if (log.isDebugEnabled()) {
          log.debug("Connection " + conn.getRealHashCode() + " is GOOD!");
        }
      } catch (Exception e) {
        log.warn("Execution of ping query '" + poolPingQuery + "' failed: " + e.getMessage());
        try {
          conn.getRealConnection().close();
        } catch (Exception e2) {
          // ignore
        }
        result = false;
        if (log.isDebugEnabled()) {
          log.debug("Connection " + conn.getRealHashCode() + " is BAD: " + e.getMessage());
        }
      }
    }
    return result;
  }

  /**
   * Unwraps a pooled connection to get to the 'real' connection
   *
   * @param conn
   *          - the pooled connection to unwrap
   * @return The 'real' connection
   */
  public static Connection unwrapConnection(Connection conn) {
    if (Proxy.isProxyClass(conn.getClass())) {
      InvocationHandler handler = Proxy.getInvocationHandler(conn);
      if (handler instanceof PooledConnection) {
        return ((PooledConnection) handler).getRealConnection();
      }
    }
    return conn;
  }

  @Override
  protected void finalize() throws Throwable {
    forceCloseAll();
    super.finalize();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(getClass().getName() + " is not a wrapper.");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) {
    return false;
  }

  @Override
  public Logger getParentLogger() {
    return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  }

}
