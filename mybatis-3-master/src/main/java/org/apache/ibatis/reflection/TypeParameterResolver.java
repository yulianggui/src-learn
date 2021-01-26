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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * https://blog.csdn.net/max_value/article/details/105041666
 * 当存在复杂的继承关系以及泛型定义时，TypeParameterResolver可以帮助我们解析字段，参数列表、返回值的类型，这是Reflector的基础
 *
 * 如下内容摘抄在 Mybatis 技术内幕 一书
 * java.lang.reflect.Type 类型的子类,jdk 提供的子类
 *   java.lang.Class: 表示的是原始类。Class 对象标识JVM 中的一个类或接口的，每一个Java 对象在JVM 里都表现为一个Class 对象
 *                     数组对象也被映射为Class 对象，所有元素类型相同且维数相同的数组都通向一个Class 对象
 *   java.lang.reflect.ParameterizedType：表示参数化类型，比如List<String>、Map<Sting,Integer> 等，指定了泛型的类型
 *                                        List<T>、Map<K,V>
 *                     ParameterizedType 接口的常用方法如下:
 *                          Type getRawType() : 返回参数化类型中的原始类型，例如List<String> 返回List类型
 *                          Type[] getActualTypeArguments(): 获取参数化类型的类型变量或者是实际类型列表。例如 Map<String,Integer>
 *                                                           的实际泛型列表Integer 和String。需要注意的是，该列表的元
 *                                                           素类型都是Type ，也就是说，可能存在多层嵌套的情况
 *                          Type getOwnerType(): 返回类型所属的类型，例如存在A<T> 类，其中定义了内部类 Inner<A>，则Inner<A>
 *                                               所属的类型为A<T>（注意是T），如果是顶层的类型则返回null。这种关系比较常
 *                                               见的实例是Map<K,V> 接口与Map.Entry<K,V>接口，Map<K,V>接口是
 *                                               Map.Entry<K,V>接口的所有者
 *   java.lang.reflect.TypeVariable：表示的是类型变量，它用来反映在JVM编译该泛型前的信息。例如List<T>中的T 就是类型变量，它
 *                                   在编译时需要被转换为一个具体的类型后才能正常使用。常用的方法如下：
 *                          Type[] getBounds()：获取类型变量的上边界，如果未明确声明上边界则默认为Object。例如class Test
 *                                              <K extends Person> 中K 的上边界就是Person
 *                          D getGenericDeclaration(): 获取声明改类型变量的原始类型，例如 class Test<K extends Person>
 *                                              中的原始类型为Test
 *                          String getName(): 获取在源码中定义时的名字，上例为K
 *   java.lang.reflect.GenericArrayType: 表示的是数组类型且组成元素是ParameterizedType 或者 TypeVariable. 例如
 *                                       List<String>[] 或者T[]。该接口只有Type getGenericComponentType()一个方法，它
 *                                       返回数组的组成元素。
 *   java.lang.reflect.WildcardType：表示的是通配符泛型，例如 ? extends Number 和 ? super Integer
 *                     WildcardType 有两个方法：
 *                          Type[] getUpperBounds()：返回泛型变量的上届
 *                          Type[] getLowerBounds(): 返回泛型变量的下届
 *
 *  疑问：
 *  1、为什么需要解析？
 *  2、resolveType 中为什么不用解析 WildcardType 类型
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * 解析字段类型
   * Resolve field type.
   *
   * @param field
   *          the field
   * @param srcType  -- 在reflector 中是 需要反射的类的 class
   *          the src type
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveFieldType(Field field, Type srcType) {
    // 获取字段的声明类型
    Type fieldType = field.getGenericType();
    // 获取字端定义所在的类 class 类型
    Class<?> declaringClass = field.getDeclaringClass();
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * 解析返回类型
   * Resolve return type.
   *
   * @param method
   *          the method
   * @param srcType
   *          the src type
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    Type returnType = method.getGenericReturnType();
    Class<?> declaringClass = method.getDeclaringClass();
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * 解析参数列表类型
   * Resolve param types.
   *
   * @param method
   *          the method
   * @param srcType
   *          the src type
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the
   *         declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  /**
   * 解析 type 的类型
   * 会根据第一个参数（type）的类型，即字段、方法的返回值或者方法参数的类型，选择合适的方法进行解析
   * @param type 待解析的类型
   * @param srcType 源类型--所属class、Type 等。 表示查找改字段、返回值或反复参数的起始位置
   * @param declaringClass type 声明的class 表示该字段、方法定义所在的类
   * @return 返回值
   */
  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
    // 次数没有 WildcardType 类型，字段、返回值、参数不可能直接定义为 WildcardType，但是可以嵌套在别的类型中 ？？？ 为什么？？
    if (type instanceof TypeVariable) {
      // (T a)泛型 (<K extends Person> a)等
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
    } else if (type instanceof ParameterizedType) {
      // List<String> 、Map<String, Object>  Map<K,V> List<T> 等
      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
      // List<String>[] 、T[]
    } else if (type instanceof GenericArrayType) {
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
      // 剩下class 类型，直接返回
      return type;
    }
  }

  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    Type componentType = genericArrayType.getGenericComponentType();
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * 解析 ParameterizedType 类型
   * @param parameterizedType 待解析的 parameterizedType 类型
   * @param srcType 解析操作的起始类型，来源类型
   * @param declaringClass parameterizedType 类型所在的Class 即定义的类
   * @return 解析后的类型
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    // 原始类型对应的Class 对象 , 比如List<T> ，则返回 java.lang.List 对应的Class 对象
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
    // 类型变量列表， 必填 String 或者 T
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
    // 用于保存解析后的结果
    Type[] args = new Type[typeArgs.length];
    // 遍历解析每一个<K, String, V > 变量等
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
        // 如果嵌套了 WildcardyyType
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
    // 创建 ParameterizedTypeImpl 对象
    // 将解析结果封装为 ParameterizedTypeImpl 对象，当前的一个内部类
    //
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 解析 TypeVariable
   * @param typeVar 待解析的类型，已经知道为 T 、K extends Person 等
   * @param srcType 示查找改字段、返回值或反复参数的起始位置
   * @param declaringClass 声明 typeVar 类型所在类的class
   * @return 返回解析后的类型
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result;
    Class<?> clazz;
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    if (clazz == declaringClass) {
      Type[] bounds = typeVar.getBounds();
      if (bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

    Type superclass = clazz.getGenericSuperclass();
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
    return Object.class;
  }

  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
      TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();
      if (srcType instanceof ParameterizedType) {
        parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
      }
      if (declaringClass == parentAsClass) {
        for (int i = 0; i < parentTypeVars.length; i++) {
          if (typeVar.equals(parentTypeVars[i])) {
            return parentAsType.getActualTypeArguments()[i];
          }
        }
      }
      if (declaringClass.isAssignableFrom(parentAsClass)) {
        return resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
      return resolveTypeVar(typeVar, superclass, declaringClass);
    }
    return null;
  }

  private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
    Type[] parentTypeArgs = parentType.getActualTypeArguments();
    Type[] srcTypeArgs = srcType.getActualTypeArguments();
    TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
    Type[] newParentArgs = new Type[parentTypeArgs.length];
    boolean noChange = true;
    for (int i = 0; i < parentTypeArgs.length; i++) {
      if (parentTypeArgs[i] instanceof TypeVariable) {
        for (int j = 0; j < srcTypeVars.length; j++) {
          if (srcTypeVars[j].equals(parentTypeArgs[i])) {
            noChange = false;
            newParentArgs[i] = srcTypeArgs[j];
          }
        }
      } else {
        newParentArgs[i] = parentTypeArgs[i];
      }
    }
    return noChange ? parentType : new ParameterizedTypeImpl((Class<?>)parentType.getRawType(), null, newParentArgs);
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
