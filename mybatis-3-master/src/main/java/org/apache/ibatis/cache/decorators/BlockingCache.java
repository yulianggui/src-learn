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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * 简单的阻塞方法
 * 根据key 获取指定缓存数据的时候，是阻塞的
 *
 * 当在缓存中找不到元素时，它将对缓存键设置锁定
 * 这样，其他线程将等待直到该元素被填充，而不是访问数据库
 *
 * 每个Key 持有一个 CountDownLatch(1) 的对象，用来 使得当前获取 key 的锁定
 * <p>Simple blocking decorator
 *
 * <p>Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * <p>By its nature, this implementation can cause deadlock when used incorrecly.
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  private long timeout;
  private final Cache delegate;
  private final ConcurrentHashMap<Object, CountDownLatch> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
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
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      // 另一个线程调用了该方法，已经将数据放入了，则将当前的key
      // 对应的 CountDownLatch 移除，并且 countDownLatch -1，使得阻塞在getObject 的线程获在阻塞的地方得到唤醒
      // 去调用 getObject 方法
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 当前线程尝试获得锁，如估获得不到，那么久阻塞或者超时阻塞
    acquireLock(key);
    Object value = delegate.getObject(key);
    if (value != null) {
      // 释放掉锁
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  private void acquireLock(Object key) {
    // newLatch 一个新的 CountDownLatch
    CountDownLatch newLatch = new CountDownLatch(1);
    // 循环等待
    while (true) {
      // 如果key 不存在，则将当前 countDownLatch 放入，尝试获得锁
      // 如果集合中已经有了 latch 对象，这使用latch 对象
      CountDownLatch latch = locks.putIfAbsent(key, newLatch);
      // 如果 latch 成功，的说明key 已经存在了，跳出循环，然后返回，不需要等待阻塞
      if (latch == null) {
        break;
      }
      try {
        // 否则如果设置了 超时，则会进行超时锁等待
        if (timeout > 0) {
          // 超时等到，如果计数达到零，则该方法返回值true
          boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
          // 如果没有获得锁，抛出异常
          if (!acquired) {
            throw new CacheException(
                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
          }
        } else {
          // 否则一直阻塞等到当前的 newLatch -1 ，被唤醒
          latch.await();
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    }
  }

  private void releaseLock(Object key) {
    CountDownLatch latch = locks.remove(key);
    if (latch == null) {
      throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
    }
    latch.countDown();
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
