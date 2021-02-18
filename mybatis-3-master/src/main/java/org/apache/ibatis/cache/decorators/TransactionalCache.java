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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *  也实现了 Cache 接口
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  private final Cache delegate;
  /**
   * 是否在提交的适合清空缓存，默认为 false
   * 清空的是 delegate 的缓存
   * 清理后{@link #clear()} 时，该值为 true ，表示持续处于清空状态
   */
  private boolean clearOnCommit;
  /**
   * 记录未真正进入到缓存时的 key - value
   * 在 session close、commit 或者这栋调用 flush 时才会从 entriesToAddOnCommit 迁移到 delegate 中
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 缓存未命中的 key
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  /**
   * 不支持手动调用 removeObject
   * @param key
   *          The key
   * @return
   */
  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    /**
     * 清空缓存，此时将 clearOnCommit 设置为 true
     */
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    // 需要清空 cache
    if (clearOnCommit) {
      delegate.clear();
    }
    flushPendingEntries();
    // 为什么要重置 ，重用 TransactionCache 对象
    reset();
  }

  /**
   * 回滚
   */
  public void rollback() {
    /**
     * 释放调用 缓存未命中的 key
     */
    unlockMissedEntries();
    // 重新将 clearOnCommit 设置为 true，并清空 entriesToAddOnCommit 、entriesMissedInCache
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 在进行 flushPendingEntries 时，才会进行缓存
   */
  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 如果存在缓存未命中的 key，则将 cache 中对应的 value 设置为 null
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 释放调用 缓存未命中的 key
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
