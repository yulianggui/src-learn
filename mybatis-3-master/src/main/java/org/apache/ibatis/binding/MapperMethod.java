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
package org.apache.ibatis.binding;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * 映射方法
 * Mapper 和 sql 中的映射关心就是这里解析之后使用该对象描述
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
public class MapperMethod {

  /**
   * sql 封装的类
   * 记录了sql 语句的名称和类型 （增删改查）
   *   name: sql 语句的名称
   *   type: 增删改查、flush
   */
  private final SqlCommand command;
  /**
   * 方法签名
   */
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  /**
   * 会根据SQL 语句类型调用SqlSession 对应的方法完成数据库的操作
   * @param sqlSession
   * @param args
   * @return
   */
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
        // 使用 ParamNameResolver 处理args[] 数组，将用户传入的实参指定名称关联起来
        Object param = method.convertArgsToSqlCommandParam(args);
        // 调用 insert 方法，rowCountResult 方法将根据 Method 字段 method 中记录的方法
        // 的返回值类型对结果进行映射
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        // 返回值为void 并且 Method 参数列表存在 ResultHandler
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
          // 返回结果为 多个的 array | collection
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          // 返回结果为@MapKey 的 Map 类型
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          // cursor
          result = executeForCursor(sqlSession, args);
        } else {
          // 单一的对象 | Map<String, Object> 等
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional()
              && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    // method.getReturnType().isPrimitive() 原始类型
    // 此方法主要用来判断Class是否为原始类型（boolean、char、byte、short、int、long、float、double）
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    // 空方法，返回空
    if (method.returnsVoid()) {
      result = null;
      // Integer | int
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  /**
   * 带有 ResultHandle.class 类型参数的查询方法
   * @param sqlSession sqlSession 会话
   * @param args 参数
   */
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    // 获得方法签名对应的ms
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    // 使用ResultHandler 处理结果集合时，必须指定 ResultMap | ResultType
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    // 有 RowBounds 参数
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      // 没有 RowBounds 参数
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    // 将结果转换为 数组或者集合
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      // 如果是数组
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    // 对象工厂创建一个对象
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    // 注意了，返回的是MetaObject
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    // 数组对应的Class 类型。不然 User[]
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[]) array);
    }
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

    private final String name;
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      // 如果没有找到
      if (ms == null) {
        // Flush 不为 null，标记为 FLUSH 类型
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        // 找到了， statementId 为name
        name = ms.getId();
        // sql 类型为 表达式
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      String statementId = mapperInterface.getName() + "." + methodName;
      // 如果有，就返回了
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
        // 如果没有，并且当前方法就是 declaringClass 声明的，则说明真的找不到
      } else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      // 指定的方法是在父结构中定义的
      // 遍历父接口，继续获得MappedStatement 对象
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        // 方法声明的类是 superInterface 的父类
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  public static class MethodSignature {

    /**
     * 是否为 collection | array
     */
    private final boolean returnsMany;
    /**
     * 返回类型是否为Map
     */
    private final boolean returnsMap;
    /**
     * 是否返回null
     */
    private final boolean returnsVoid;
    /**
     * 是否返回类型为 cursor 类型
     */
    private final boolean returnsCursor;
    /**
     * 是否返回类型为 Optional
     */
    private final boolean returnsOptional;
    /**
     * 返回类型
     */
    private final Class<?> returnType;
    /**
     *  如果返回类型为Map，且@MapKey 注解存在，则为 @MapKey 的values
     *  Map<Integer, User> @MapKey ， Id 作为key
     */
    private final String mapKey;
    /**
     * 用来标记方法参数列表中ResultHandler 类型参数的位置
     * ResultHandler 在 Method 参数中的位置
     * ResultHandler 和 RowBounds 参数只能有一个，不能重复出现
     */
    private final Integer resultHandlerIndex;
    /**
     *  用来标记方法参数中RowBounds 类型参数的位置
     *  接 Method(RowBounds.class, Integer.Class)
     *  则RowBounds = 0
     */
    private final Integer rowBoundsIndex;
    /**
     * 解析Method 参数的类
     */
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 解析返回类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
        // 泛型 List<User>、Map<String, Object>
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        // 返回值
        this.returnType = method.getReturnType();
      }
      // 是否返回为空
      this.returnsVoid = void.class.equals(this.returnType);
      // 返回类型是否未多个 ，Array | collection
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      // 是否为 Cursor 类型
      this.returnsCursor = Cursor.class.equals(this.returnType);
      // 是否为 Optional 类型 jdk1.8
      this.returnsOptional = Optional.class.equals(this.returnType);
      // 返回值是否为 Map 类型，@MapKey 的values
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      // 用来标记方法参数中 RowBounds 类型参数的位置
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      // 用来标记方法参数列表中ResultHandler 类型参数的位置
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      // 对应 ParamNameResolver
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    /**
     * 主要功能是查找指定类型的参数在列表中的位置
     * @param method 方法
     * @param paramType 参数类型
     * @return 返回值
     */
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index == null) {
            index = i;
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      // 如果 返回类型为 Map 类型
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        // 并且添加了 MapKey 注解
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
