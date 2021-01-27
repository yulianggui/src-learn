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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class MetaClass {

  /**
   * 构造函数传入 reflector 工厂，用于创建 Reflector
   */
  private final ReflectorFactory reflectorFactory;
  /**
   * 在构造方法中会初始化，从 reflectorFactory 工厂中根据 Type 获取一个针对于该clazz 的 Reflector 对象
   */
  private final Reflector reflector;

  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  /**
   * 静态方法调用，本质是调用私有的构造返回
   * @param type 需要进行反射操作的对象类型
   * @param reflectorFactory 返回工厂
   * @return 返回MeteClass 信息
   */
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 获取 type 类型中某个属性类型的 MetaClass 源数据Class 信息
   * @param name 属性名称
   * @return 返回
   */
  public MetaClass metaClassForProperty(String name) {
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * user.dept[0].name == user.dept.name ，即属性分词器中的 index 将会忽略
   * @param name 属性名称，可以是复杂的表达式名称
   * @return 返回解析到的属性，比如上述也可能只解析得到部分，比如  user.dept 而找不到 name
   */
  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  /**
   * 查找属性
   * @param name 属性名称
   * @param useCamelCaseMapping 是否匹配驼峰的
   * @return 返回值
   */
  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 如果使用驼峰，则现将 name 的下划线去掉
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  /**
   * 获取 type 类型的 getter 属性名称
   * @return 返回数组
   */
  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  /**
   * user.dept.name
   *   者返回 name 的类型 java.lang.String
   * 属性表达式获取对应的 setter 方法参数的Type
   * @param name 属性名称，可以是属性表达式，找到最后一个属性的类型
   * @return 返回属性的类型
   */
  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      // 递归查找最后的类型
      return metaProp.getSetterType(prop.getChildren());
    } else {
      // 最后一层了，分词属性解析器中已经没有children 了
      return reflector.getSetterType(prop.getName());
    }
  }

  /**
   * 得到属性的返回值类型
   * @param name 属性名称
   * @return 返回值类型
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    Class<?> type = reflector.getGetterType(prop.getName());
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // index 存在，list[0] Collection = list
      Type returnType = getGenericGetterType(prop.getName());
      // 如果返回类型是 List<String> 、List<User> 明确的类型
      if (returnType instanceof ParameterizedType) {
        // 获取参数化类型的类型变量或者是实际类型列表
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        // 为什么这里判断大小为 1 呢，因为 Collection 是 Collection<T> ，至多一
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            // 得到参数化类型中的的原始类型 比如 List<User> 此时为 User
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      // 获取调用方法
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 如果是原始类型，不存在二义性
      if (invoker instanceof MethodInvoker) {
        // 如果存在属性方法
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        // 设置可访问
        declaredMethod.setAccessible(true);
        // 相当于 invoker.getMethod(); 方法
        Method method = (Method) declaredMethod.get(invoker);
        // 解析方法的返回类型
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        // 不存在属性方法，但是存在属性字段
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        // 相当于直接获取字段 class.fieLd 字段
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  /**
   * 是否存在setter 方法
   * @param name 属性方法
   * @return 返回值 为布尔值
   */
  public boolean hasSetter(String name) {
    // 分词解析器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 解析子节点
      if (reflector.hasSetter(prop.getName())) {
        // 读源码的时候要记住， name 属性 是否有 set|get 这些方法，类型，都在 reflector 中封装了
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  /**
   * 是否存在对应的属性，而 getGetterType 是重载
   * get 会复杂一些，因为可能是泛型Collection 参数，需要拿到具体泛型的第一个泛型参数信息 List<User> user.name
   * @param name
   * @return
   */
  public boolean hasGetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasGetter(prop.getName())) {
        // 再次用到分词属性
        MetaClass metaProp = metaClassForProperty(prop);
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * user.dept[0].name
   * 第一步：
   *      class 为 User.class
   *      builder = user + "."
   *         children=dept[0].name
   * 第二步：
   *      class 为 Dept.Class
   *      builder= user.dept + "."
   *         children=name
   * 第三部：
   *      class 为 String
   *      builder=user.dept.name
   *       children=null 即prop.hasNext()=false
   * @param name
   * @param builder
   * @return
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 分词解析器
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 如果还有子节点 子节点同样可能是组合
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 如果找到了
        builder.append(propertyName);
        builder.append(".");
        // 查看当前节点的类型，然后还要遍历子节点的属性名称
        MetaClass metaProp = metaClassForProperty(propertyName);
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
