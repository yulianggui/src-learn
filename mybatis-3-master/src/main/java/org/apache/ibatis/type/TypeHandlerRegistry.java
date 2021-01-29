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
package org.apache.ibatis.type;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.chrono.JapaneseDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;

/**
 * TypeHandler 注册器
 * configuration 持有
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {

  /**
   * 记录JdbcType 与 TypeHandler 之间的关系，其中JdbcType 是一个枚举类型，它定义了对应的JDBC 类型
   * 该集合主要用于从结果集读取数据时，将数据从JDBC 类型转换成为JAVA 类型
   */
  private final Map<JdbcType, TypeHandler<?>>  jdbcTypeHandlerMap = new EnumMap<>(JdbcType.class);
  /**
   * 记录了JAVA 类型向指定JdbcType 转换时，需要使用的 TypeHandler 对象。 例如： Java 类型中的String 可能
   * 转换成数据库中的 char 、varchar 等多种类型，所以存在一对多的关心
   *   String： {
   *     char -- CharTypeHandler
   *     varchar -- VarcharTypeHandler
   *   }
   */
  private final Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap = new ConcurrentHashMap<>();
  /**
   * 未知的处理类型
   */
  private final TypeHandler<Object> unknownTypeHandler;
  /**
   * 记录全部TypeHandler 的类型以及该类型相应的TypeHandler 对象
   */
  private final Map<Class<?>, TypeHandler<?>> allTypeHandlersMap = new HashMap<>();
  /**
   * 空 TypeHandler 集合的标识
   */
  private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = Collections.emptyMap();

  /**
   * 默认的枚举类型处理器
   */
  private Class<? extends TypeHandler> defaultEnumTypeHandler = EnumTypeHandler.class;

  /**
   * The default constructor.
   */
  public TypeHandlerRegistry() {
    this(new Configuration());
  }

  /**
   * The constructor that pass the MyBatis configuration.
   *
   * @param configuration a MyBatis configuration
   * @since 3.5.4
   */
  public TypeHandlerRegistry(Configuration configuration) {
    // 未知的类型？？？ 未知
    this.unknownTypeHandler = new UnknownTypeHandler(configuration);

    // 注册JavaType 类型
    register(Boolean.class, new BooleanTypeHandler());
    register(boolean.class, new BooleanTypeHandler());
    // 注册对应的JdbcType 类型
    register(JdbcType.BOOLEAN, new BooleanTypeHandler());
    register(JdbcType.BIT, new BooleanTypeHandler());

    register(Byte.class, new ByteTypeHandler());
    register(byte.class, new ByteTypeHandler());
    register(JdbcType.TINYINT, new ByteTypeHandler());

    register(Short.class, new ShortTypeHandler());
    register(short.class, new ShortTypeHandler());
    register(JdbcType.SMALLINT, new ShortTypeHandler());

    register(Integer.class, new IntegerTypeHandler());
    register(int.class, new IntegerTypeHandler());
    register(JdbcType.INTEGER, new IntegerTypeHandler());

    register(Long.class, new LongTypeHandler());
    register(long.class, new LongTypeHandler());

    register(Float.class, new FloatTypeHandler());
    register(float.class, new FloatTypeHandler());
    register(JdbcType.FLOAT, new FloatTypeHandler());

    register(Double.class, new DoubleTypeHandler());
    register(double.class, new DoubleTypeHandler());
    register(JdbcType.DOUBLE, new DoubleTypeHandler());

    register(Reader.class, new ClobReaderTypeHandler());
    register(String.class, new StringTypeHandler());
    // StringTypeHandler 可以将JdbcType 类型可以将数据转换为 null 类型
    // 所以 typeHandlerMap String = map{
    //
    // jdbcVarchar, StringTypeHandler
    // CLOB, StringTypeHandler
    // VARCHAR, StringTypeHandler
    // LONGVARCHAR,StringTypeHandler
    // NCHAR,StringTypeHandler
    // NCLOB,StringTypeHandler
    // }
    register(String.class, JdbcType.CHAR, new StringTypeHandler());
    register(String.class, JdbcType.CLOB, new ClobTypeHandler());
    register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
    register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
    register(JdbcType.CHAR, new StringTypeHandler());
    register(JdbcType.VARCHAR, new StringTypeHandler());
    register(JdbcType.CLOB, new ClobTypeHandler());
    register(JdbcType.LONGVARCHAR, new StringTypeHandler());
    register(JdbcType.NVARCHAR, new NStringTypeHandler());
    register(JdbcType.NCHAR, new NStringTypeHandler());
    register(JdbcType.NCLOB, new NClobTypeHandler());

    register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
    register(JdbcType.ARRAY, new ArrayTypeHandler());

    register(BigInteger.class, new BigIntegerTypeHandler());
    register(JdbcType.BIGINT, new LongTypeHandler());

    register(BigDecimal.class, new BigDecimalTypeHandler());
    register(JdbcType.REAL, new BigDecimalTypeHandler());
    register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
    register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

    register(InputStream.class, new BlobInputStreamTypeHandler());
    register(Byte[].class, new ByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
    register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
    register(byte[].class, new ByteArrayTypeHandler());
    register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
    register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
    register(JdbcType.BLOB, new BlobTypeHandler());

    register(Object.class, unknownTypeHandler);
    register(Object.class, JdbcType.OTHER, unknownTypeHandler);
    register(JdbcType.OTHER, unknownTypeHandler);

    register(Date.class, new DateTypeHandler());
    register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
    register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
    register(JdbcType.TIMESTAMP, new DateTypeHandler());
    register(JdbcType.DATE, new DateOnlyTypeHandler());
    register(JdbcType.TIME, new TimeOnlyTypeHandler());

    register(java.sql.Date.class, new SqlDateTypeHandler());
    register(java.sql.Time.class, new SqlTimeTypeHandler());
    register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

    register(String.class, JdbcType.SQLXML, new SqlxmlTypeHandler());

    register(Instant.class, new InstantTypeHandler());
    register(LocalDateTime.class, new LocalDateTimeTypeHandler());
    register(LocalDate.class, new LocalDateTypeHandler());
    register(LocalTime.class, new LocalTimeTypeHandler());
    register(OffsetDateTime.class, new OffsetDateTimeTypeHandler());
    register(OffsetTime.class, new OffsetTimeTypeHandler());
    register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
    register(Month.class, new MonthTypeHandler());
    register(Year.class, new YearTypeHandler());
    register(YearMonth.class, new YearMonthTypeHandler());
    register(JapaneseDate.class, new JapaneseDateTypeHandler());

    // issue #273
    register(Character.class, new CharacterTypeHandler());
    register(char.class, new CharacterTypeHandler());
  }

  /**
   * Set a default {@link TypeHandler} class for {@link Enum}.
   * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
   * @param typeHandler a type handler class for {@link Enum}
   * @since 3.4.5
   */
  public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
    this.defaultEnumTypeHandler = typeHandler;
  }

  public boolean hasTypeHandler(Class<?> javaType) {
    return hasTypeHandler(javaType, null);
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
    return hasTypeHandler(javaTypeReference, null);
  }

  public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
    return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
  }

  public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
    return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
  }

  public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
    return allTypeHandlersMap.get(handlerType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
    return getTypeHandler((Type) type, null);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
    return getTypeHandler(javaTypeReference, null);
  }

  public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
    return jdbcTypeHandlerMap.get(jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
    return getTypeHandler((Type) type, jdbcType);
  }

  public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
    return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
  }

  @SuppressWarnings("unchecked")
  private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
    // 如果MapperMethod mapper 方法的参数Map ，返回
    // org.apache.ibatis.binding.MapperMethod.ParamMap
    if (ParamMap.class.equals(type)) {
      return null;
    }
    // 查找java 类型对应的jdbc 处理器集合
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
    TypeHandler<?> handler = null;
    if (jdbcHandlerMap != null) {
      // 可以找得到 java 对应的集合
      handler = jdbcHandlerMap.get(jdbcType);
      // 但是可能没有指定jdbcType 对应的类型，尝试去使用 key 为 NULL 的jdbcType 类型
      if (handler == null) {
        handler = jdbcHandlerMap.get(null);
      }
      // 如果还是找不到，尝试挑选一个合适的
      if (handler == null) {
        // #591
        handler = pickSoleHandler(jdbcHandlerMap);
      }
    }
    // type drives generics here
    return (TypeHandler<T>) handler;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(type);
    // 如果是个空集合，说明曾经调用过该类型进行了查找，但是没有找到
    // 不必再进行下面查找了
    if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
      return null;
    }
    // 没找到 java 对应的 jdbcHandlerMap
    if (jdbcHandlerMap == null && type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      if (Enum.class.isAssignableFrom(clazz)) {
        // 如果当前java 是枚举类型，如果该枚举是匿名类，获取起父类，否则当前枚举类
        Class<?> enumClass = clazz.isAnonymousClass() ? clazz.getSuperclass() : clazz;
        // 获取枚举类型的处理映射器
        // 找到枚举原始类型 enumClass == map<jdbcType， TypeHandler>
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(enumClass, enumClass);
        if (jdbcHandlerMap == null) {
          // 如果没有找到，注册一个默认的
          register(enumClass, getInstance(enumClass, defaultEnumTypeHandler));
          return typeHandlerMap.get(enumClass);
        }
      } else {
        // 非枚举类，从父类中找到到该 javaType 与 JdbcType 对应的 TypeHandler
        jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
      }
    }
    // 如果没找到，返回一个空的  Map<JdbcType, TypeHandler<?>>
    typeHandlerMap.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
    return jdbcHandlerMap;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz, Class<?> enumClazz) {
    //
    // 从接口中找
    for (Class<?> iface : clazz.getInterfaces()) {
      Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(iface);
      if (jdbcHandlerMap == null) {
        // 没找到继续递归，传入接口类型，元素类型
        jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface, enumClazz);
      }
      // 找到了
      if (jdbcHandlerMap != null) {
        // Found a type handler regsiterd to a super interface
        HashMap<JdbcType, TypeHandler<?>> newMap = new HashMap<>();
        for (Entry<JdbcType, TypeHandler<?>> entry : jdbcHandlerMap.entrySet()) {
          // Create a type handler instance with enum type as a constructor arg
          // 构造一份原始类 enumClazz 对应的TypeHandler 返回
          // enumClazz 为 java 类型，即 实例化一个可以处理 enumClazz 类型的TypeHandler ， 类型的TypeHandler 就是 entry.getValue().getClass()
          // 通过枚举的接口找到的
          newMap.put(entry.getKey(), getInstance(enumClazz, entry.getValue().getClass()));
        }
        // 只要其中一个接口满足了，就返回
        return newMap;
      }
    }
    return null;
  }

  private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
    Class<?> superclass =  clazz.getSuperclass();
    // 没有父类，且不为 Object
    if (superclass == null || Object.class.equals(superclass)) {
      return null;
    }
    Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = typeHandlerMap.get(superclass);
    if (jdbcHandlerMap != null) {
      return jdbcHandlerMap;
    } else {
      // 找不到继续递归找
      return getJdbcHandlerMapForSuperclass(superclass);
    }
  }

  /**
   * 挑选一个唯一的 jdbcType 类型处理器
   * @param jdbcHandlerMap jdbc 类型处理器
   * @return
   */
  private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
    TypeHandler<?> soleHandler = null;
    for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
      if (soleHandler == null) {
        soleHandler = handler;
      } else if (!handler.getClass().equals(soleHandler.getClass())) {
        // More than one type handlers registered.
        // 超过一个处理器，返回null
        return null;
      }
    }
    return soleHandler;
  }

  public TypeHandler<Object> getUnknownTypeHandler() {
    return unknownTypeHandler;
  }

  /**
   * 注册到 jdbcTypeHandlerMap
   * @param jdbcType jdbcType
   * @param handler handler
   */
  public void register(JdbcType jdbcType, TypeHandler<?> handler) {
    jdbcTypeHandlerMap.put(jdbcType, handler);
  }

  //
  // REGISTER INSTANCE
  //

  // Only handler

  @SuppressWarnings("unchecked")
  public <T> void register(TypeHandler<T> typeHandler) {
    // MappedTypes 注解作用月 TypeHandler 处理器上
    // 该处理器是否有 @MappedTypes 注解
    boolean mappedTypeFound = false;
    // MappedTypes 注解的values 为 javaType 类型的 class
    MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> handledType : mappedTypes.value()) {
        register(handledType, typeHandler);
        mappedTypeFound = true;
      }
    }
    // @since 3.1.0 - try to auto-discover the mapped type
    // 1、如果存在 MappedTypes 注解，不会走这个逻辑
    // 2、并且要求 typeHandler TypeReference 类型
    // 这里的功能是尝试去自动发现 javaType 与 TypeHandler 的映射关系
    if (!mappedTypeFound && typeHandler instanceof TypeReference) {
      try {
        TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
        // 返回参数化类型中的原始类型，例如List<String> 返回List类型
        // LongTypeHandler T 为<Long> --》 返回 泛型参数第一个参数类型
        register(typeReference.getRawType(), typeHandler);
        mappedTypeFound = true;
      } catch (Throwable t) {
        // 可能用户定义了不同类型的TypeReference，并且不能赋值
        // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
      }
    }
    // 如果没找到， 则作为 javaType 为null 时的 处理器
    if (!mappedTypeFound) {
      register((Class<T>) null, typeHandler);
    }
  }

  // java type + handler

  public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
    register((Type) javaType, typeHandler);
  }

  private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
    // 获取是否存在 MappedJdbcTypes 注解
    MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
    if (mappedJdbcTypes != null) {
      // 存在，遍历 mappedJdbcTypes 的values 属性
      for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
        // 注册 javaType ， jdbcType ，Handler
        register(javaType, handledJdbcType, typeHandler);
      }
      // 是否允许映射null 的JdbcType 类型
      if (mappedJdbcTypes.includeNullJdbcType()) {
        register(javaType, null, typeHandler);
      }
    } else {
      // 否则 ， map 的key 可以为 NULL，一个 javaType 类型，只有一个对应的 typeHandler 类型处理器
      register(javaType, null, typeHandler);
    }
  }

  public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
    register(javaTypeReference.getRawType(), handler);
  }

  // java type + jdbc type + handler

  // Cast is required here
  @SuppressWarnings("cast")
  public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
    register((Type) type, jdbcType, handler);
  }

  /**
   * 注册 typeHandlerMap ： javaType = map{jdbcType, Handler}  一个java 类型可能对应多个 JdbcType
   *       String  char、varchar、int
   * 注册 allTypeHandlersMap 又有的 TypeHandler 处理器，把处理器都注册到缓存中
   * @param javaType java Type
   * @param jdbcType jdbc 类型
   * @param handler 对应的转换处理器
   */
  private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
    if (javaType != null) {
      Map<JdbcType, TypeHandler<?>> map = typeHandlerMap.get(javaType);
      if (map == null || map == NULL_TYPE_HANDLER_MAP) {
        map = new HashMap<>();
      }
      map.put(jdbcType, handler);
      typeHandlerMap.put(javaType, map);
    }
    allTypeHandlersMap.put(handler.getClass(), handler);
  }

  //
  // REGISTER CLASS
  //

  // Only handler type

  public void register(Class<?> typeHandlerClass) {
    boolean mappedTypeFound = false;
    MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
    if (mappedTypes != null) {
      for (Class<?> javaTypeClass : mappedTypes.value()) {
        register(javaTypeClass, typeHandlerClass);
        mappedTypeFound = true;
      }
    }
    if (!mappedTypeFound) {
      register(getInstance(null, typeHandlerClass));
    }
  }

  // java type + handler type

  public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
    register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
  }

  public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
  }

  // java type + jdbc type + handler type

  public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
    register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
  }

  // Construct a handler (used also from Builders)
  // 构造一个handler 尝试涨到默认 构造方法， Class 参数-- 类型的构造方法
  @SuppressWarnings("unchecked")
  public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
    if (javaTypeClass != null) {
      try {
        Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
        return (TypeHandler<T>) c.newInstance(javaTypeClass);
      } catch (NoSuchMethodException ignored) {
        // ignored
      } catch (Exception e) {
        throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
      }
    }
    try {
      Constructor<?> c = typeHandlerClass.getConstructor();
      return (TypeHandler<T>) c.newInstance();
    } catch (Exception e) {
      throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
    }
  }

  // scan

  /**
   * 扫描报下的TypeHandler 类型转换器注册
   * @param packageName 包名称
   */
  public void register(String packageName) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
    Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
    for (Class<?> type : handlerSet) {
      //Ignore inner classes and interfaces (including package-info.java) and abstract classes
      // 忽略内部类、以及接口 、抽象类
      if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
        register(type);
      }
    }
  }

  // get information

  /**
   * Gets the type handlers.
   * 获取所有的 handler 映射起
   * @return the type handlers
   * @since 3.2.2
   */
  public Collection<TypeHandler<?>> getTypeHandlers() {
    return Collections.unmodifiableCollection(allTypeHandlersMap.values());
  }

}
