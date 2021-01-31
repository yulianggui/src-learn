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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class SoftCache implements Cache {
  /**
   * 在SoftCache 中， 最近使用的一部分缓存项不会被GC 回收，这就是通过将其 Value 添加到
   * hardLinksToAvoidGarbageCollection 集合中实现的，（即有强引用指向其value）
   * hardLinksToAvoidGarbageCollection 集合时 LinkedList 类型
   *
   * 然而实际上在本类中的 get 方法并没有使用，只是记录一下而已
   */
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  /**
   * 引用队列，用于记录已经被GC 回收的缓存项所对应的 SoftEntry 对象
   */
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  private final Cache delegate;
  /**
   * 强连接的个数，默认是 256
   */
  private int numberOfHardLinks;

  public SoftCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    // 向缓存中添加缓存项，还会清除已经被GC回收项的缓存
    removeGarbageCollectedItems();
    // 添加一个 SoftEntry 包装的 value
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
  }

  /**
   * 除了从缓存中查找到对应的value ，处理被GC 回收的value 对应的缓存想，还好更新 hardLinksToAvoidGarbageCollection
   * @param key
   *          The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    Object result = null;
    /**
     * 从缓存中查找对应的缓存项
     * 底层缓存的Map 是强引用，所以 softReference 是可能存在的（注意，存放的是 SoftEntry ，而真正弱引用的是 value ）
     * 但是由于 value 是被 SoftReference 包装的，所以可能真正的value 已经不存在了
     */
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cach
    SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
    if (softReference != null) {
      // 如果softReference 还存在
      result = softReference.get();
      if (result == null) {
        // 但是啊，value 已经不存在了，那么是要真正删除 底层的key 对应的value 的
        // 说明此时已经被GC 清除了
        delegate.removeObject(key);
      } else {
        // 还没有被GC 清楚，还能再挣扎一下
        // See #586 (and #335) modifications need more than a read lock
        synchronized (hardLinksToAvoidGarbageCollection) {
          hardLinksToAvoidGarbageCollection.addFirst(result);
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
            // 将最老的进行清除
            hardLinksToAvoidGarbageCollection.removeLast();
          }
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();
    }
    removeGarbageCollectedItems();
    delegate.clear();
  }

  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    // 从队列中拿出来，然后再进行删除
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  /**
   * SoftCache 中缓存的value 是 SoftEntry 对象，SoftEntry 继承了 SoftReference，其中
   * 指向key 的引用是强引用，而指向 value 的引用是软引用
   */
  private static class SoftEntry extends SoftReference<Object> {
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      // 强引用
      this.key = key;
    }
  }

}
