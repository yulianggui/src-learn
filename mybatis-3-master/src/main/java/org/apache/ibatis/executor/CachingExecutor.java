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

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 在 sqlsession 中，commit 、close、rollback 都会调用 Executor 的相应方法
 * <setting name="cacheEnabled" value="true"/>
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

  private final Executor delegate;
  /**
   * 事务缓存管理器
   * 因为二级缓存是支持跨 Session 进行共享，此处需要考虑事务，那么，必然需要做到事务提交时，
   * 才将当前事务中查询时产生的缓存，同步到二级缓存中。这个功能，就通过 TransactionalCacheManager 来实现
   */
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    delegate.setExecutorWrapper(this);
  }

  /**
   * 获取事务，事务为 从 delegate 中获取，这个也值得学习
   * @return
   */
  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  /**
   * 二级缓存失效
   * 同时一级缓存也会失效
   * @param forceRollback
   */
  @Override
  public void close(boolean forceRollback) {
    try {
      // issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      } else {
        // 调用 tcm 提交事务，进行二级缓存保存
        tcm.commit();
      }
    } finally {
      // 最终都会调用 delegate.close ，即一级缓存失效
      delegate.close(forceRollback);
    }
  }

  /**
   * isClosed 关键在于 SimpleExecute
   * @return
   */
  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    /**
     * 如果有必要，则清除二级缓存
     */
    flushCacheIfRequired(ms);
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    /**
     * 游标的要清除缓存
     */
    flushCacheIfRequired(ms);
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    // 创建二级缓存的 key
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    // 获取二级缓存
    Cache cache = ms.getCache();
    if (cache != null) {
      // 如果需要清空缓存，则进行清空
      flushCacheIfRequired(ms);
      if (ms.isUseCache() && resultHandler == null) {
        ensureNoOutParams(ms, boundSql);
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        return list;
      }
    }
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  @Override
  public void commit(boolean required) throws SQLException {
    // 提交事务
    delegate.commit(required);
    tcm.commit();
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      delegate.rollback(required);
    } finally {
      if (required) {
        tcm.rollback();
      }
    }
  }

  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  /**
   * 延迟加载本质是调用 原始的 Executor 方法
   * @param ms
   * @param resultObject
   * @param property
   * @param key
   * @param targetType
   */
  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  private void flushCacheIfRequired(MappedStatement ms) {
    /**
     * 获取二级缓存的实现类
     */
    Cache cache = ms.getCache();
    // 存在二级缓存，并且 flushCacheRequired = true，select 默认为 false,update 默认为 true 、 insert
    if (cache != null && ms.isFlushCacheRequired()) {
      // 清空二级缓存，清除当前事务中的 entriesToAddOnCommit 的缓存，并不是 cache 中的缓存，此时也会将 clearOnCommit 设置为  true
      // 而 clearOnCommit 将会在 commit 和 rollback 的方法中通过掉头 reset 方法重新设置为 false
      // clearOnCommit 为 true ，意味着，tcm#getObject 当前的 cache 的getObject 返回结果为为 null


      // 这里为什么只会清空 当前事务的 entriesToAddOnCommit 呢
      // 1、entriesToAddOnCommit 记录的是当前 事务中 tcm cache 对应的，未进入到 全局二级缓存（即 cache，多个 session 中的 cache 是同一个）中待缓存对象
      // 2、当 executor#commit 时，调用 tcm#commit ，遍历 TransactionalCache#txCache，如果此时 cache 对应的 clearOnCommit 为 true，则先将 cache 情空缓存，然后将
      // 当前的 事务结果写入 flushPendingEntries cache 缓存中
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}
