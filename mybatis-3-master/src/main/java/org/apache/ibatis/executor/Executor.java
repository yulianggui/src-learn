/**
 *    Copyright 2009-2015 the original author or authors.
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

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 执行器，sql 执行器的入口
 *   主要负责一级缓存和二级缓存的维护
 *     并提供事务管理的相关操作，它会将数据库相关的操作委托给 StatementHandler 完成
 *
 * 默认的实现为
 * @author Clinton Begin
 */
public interface Executor {

  /**
   * 空的 ResultHandler 对象
   */
  ResultHandler NO_RESULT_HANDLER = null;

  /**
   * 更新 或者 插入 或者 删除，有传入的 MappedStatement 类型决定
   *
   * @param ms MapperStatement 悐
   * @param parameter 参数
   * @return 返 int
   * @throws SQLException 执行 sql
   */
  int update(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 查询，带有 ResultHandler + cacheKey + BoundSql
   * @param ms sql -> MappedStatement 描述信息。对象信息
   * @param parameter 参数
   * @param rowBounds rowBounds 对象，分页对象
   * @param resultHandler 处理器
   * @param cacheKey 缓存 key
   * @param boundSql 执行的 Sql
   * @param <E> 泛型
   * @return 返回 泛型 List
   * @throws SQLException 异常
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  /**
   * 查询，返回 游标列表
   * @param ms  sql -> MappedStatement 描述信息。对象信息
   * @param parameter 参数
   * @param rowBounds rowBounds 对象，分页对象
   * @param <E> 泛型
   * @return 返回值
   * @throws SQLException 异常
   */
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  /**
   * 刷如批处理语句
   * 批量执行 SQL 语句
   * @return 返回批量插入结构
   * @throws SQLException 异常
   */
  List<BatchResult> flushStatements() throws SQLException;

  /**
   * 提交事务
   * @param required 是否需要提交
   * @throws SQLException 异常
   */
  void commit(boolean required) throws SQLException;

  void rollback(boolean required) throws SQLException;

  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  /**
   * 判断是否缓存
   * @param ms sql 解析对象
   * @param key 缓存key
   * @return 返回 true
   */
  boolean isCached(MappedStatement ms, CacheKey key);

  void clearLocalCache();

  /**
   * 延迟加载一级缓存中的数据
   * BaseExecutor中缓存除了缓存结果集以外，在分析嵌套查询时，如果一级缓存中缓存了嵌套查询的结果对象，则可以从一级缓存中直接加载该结果对象。
   * 如果一级缓存中记录的嵌套查询的结果对象并未完全加载，则可以通过 DeferredLoad 实现类实现延迟加载的功能。与这个流程相关的方法有两个，
   * isCached 方法负责检测是否缓存了指定查询的结果对象，deferLoad方法负责创建DeferredLoad对象并添加到deferredLoad集合中。
   * @param ms
   * @param resultObject
   * @param property
   * @param key
   * @param targetType
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  Transaction getTransaction();

  void close(boolean forceRollback);

  boolean isClosed();

  /**
   * 设置包装 executor 的对象
   * @param executor 包装的对象
   */
  void setExecutorWrapper(Executor executor);

}
