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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 参数名称解析器
 * @param
 * #{id}
 * param1,param2,param3
 * 1,2,3,4,5
 * list
 * map
 * set
 */
public class ParamNameResolver {

  /**
   * 同用的参数名称前缀
   */
  public static final String GENERIC_NAME_PREFIX = "param";

  /**
   * 是否使用真正的参数名称
   */
  private final boolean useActualParamName;

  /**
   * key 是参数顺序
   * value 为参数名称
   * 注意：SortedMap 是有序的
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  /**
   * 是否有@Param 注解
   */
  private boolean hasParamAnnotation;

  /**
   * 构造方法
   * @param config mybatis 全局config
   * @param method 方面名称
   */
  public ParamNameResolver(Configuration config, Method method) {
    this.useActualParamName = config.isUseActualParamName();

    // 获取参数类型列表
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取方法参数注解集合
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    // 保存参数信息  Key 为参数索引， value 为参数名称
    // add(int a, int b)  ==> 0,a 1,b
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 是否是特别的参数类型： RowBounds 或者 ResultHandler 的父类，如果是，跳过
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 是否有 @Param 注解，如果有name（参数名称== #{name}） 为注解 Param 中标注的值
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }
      // 如果 通过注解中没找到
      if (name == null) {
        // @Param was not specified.
        // 没有使用 @Param 注解。 useActualParamName 默认是开启的
        if (useActualParamName) {
          // 如果需要启用真实的参数名称，其实是通过索引，找到 Method 参数名称中具体的名称 ParamNameUtil 工具类找
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          // 如果还没有找到，则 name 为当前map 的 大小，比如 0，1，2，3
          // 样例： 0,0 、1,1、2,id、3,3、4,depId
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    // names 为不可需改的 Map ，只能读
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  /**
   * 在构造函数中已经初始化完毕 names 了
   * @return 返回值
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * 获得参数名与值的映射
   *  1、返回 null、此时 Method 为无惨、或者 参数Object[] 为 null
   *  2、返回 没有@Param 标注，并且只有一个参数。names 的第一个Key，即 0，此时Mybatis 是不会去解析SQL 中的 #{id.name} 、${tableName} 的，默认都将会将传入的参数
   *     作为 id  tableName 赋值
   *  3、返回一个Map
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args
   *          the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    // names 的参数个数
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      // 没有开启注解，并且只有一个参数。直接返回 0 了。只有一个参数是， Mybatis 不会去解析 #{}、${}
      Object value = args[names.firstKey()];
      // 如果使用了真实的参数名称，这 wrapToMapIfCollection(value, names.get(0))，否则 wrapToMapIfCollection(value, null)
      // names.get(0) 即索引为0 对应的 value，此处会原封不动的返回 value，即为 0
      return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
    } else {
      // 否则返回一个Map
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        // names 样例： 0,0 、1,1、2,id、3,3、4,depId
        // param 结果：0,args[0] 、1,args[1]、id,args[2]、3,args[3]、depId,args[4]
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 同时提供一个通用的参数 param1,param2,param3
        // 对应参数列表 Method(param1,param2,param3)
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * 1、如果是个 Collection ，则 返回 collection 并且如果是List list，即如果为 list，则 返回Map ，且collection、list 都可以解析 #{collection} 、#{list}
   *   同时如果 actualParamName 不为null，也会将 name 存入到 map，接 #{name} 也会解析得出来
   * 2、如果是个数组
   *   则array == #{array}
   *   同时如果 actualParamName 不为null，也会将 name 存入到 map，接 #{name} 也会解析得出来
   * 总之这里目前都是Map 包装的
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object a parameter object
   * @param actualParamName an actual parameter name
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    } else if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

}
