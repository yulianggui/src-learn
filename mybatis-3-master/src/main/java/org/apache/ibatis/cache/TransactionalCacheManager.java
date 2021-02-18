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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * 这里其实就是简单的维护了一个
 * Map<Cache, TransactionalCache> 同一个 Cache 对象，对应一个 TransactionalCache
 * Cache 对象是早就缓存好在 Configuration#MappedStatment 中的，可以在多个 Session 中的不同 CachingExecutor 中可能被用到同一个 cache 对象
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  /**
   * 获取的是 cache 对象的 TransactionalCache ，而 TransactionalCache 又会持有 cache 对象
   * 其实在 getObject 方法中，最终还是由 cache 的 getObject 方法返回 缓存的对象
   * @param cache
   * @param key
   * @return
   */
  public Object getObject(Cache cache, CacheKey key) {
    return getTransactionalCache(cache).getObject(key);
  }

  public void putObject(Cache cache, CacheKey key, Object value) {
    getTransactionalCache(cache).putObject(key, value);
  }

  public void commit() {
    // 提交事务 TransactionalCache#commit 方法
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  public void rollback() {
    // 回滚事务 TransactionalCache#rollback
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  /**
   * 获取 TransactionalCache ，如果没有，则调用 Cache 参数 构造方法，创建一个
   * @param cache
   * @return
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
