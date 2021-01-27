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
package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;

/**
 * Mybatis 中 使用 该接口 创建需要的新对象
 *    即Map 映射返回的对象等都通过该对象工厂去实例化
 *    用户可以进行自己实现覆盖，不过要么是闲的没事干，不然不会这样搞
 * MyBatis uses an ObjectFactory to create all needed new Objects.
 *
 * @author Clinton Begin
 */
public interface ObjectFactory {

  /**
   * 配置信息
   * Sets configuration properties.
   * @param properties configuration properties
   */
  default void setProperties(Properties properties) {
    // NOP
  }

  /**
   * 以默认的构造方法创建对象
   *
   * Creates a new object with default constructor.
   *
   * @param <T>
   *          the generic type
   * @param type
   *          Object type
   * @return the t
   */
  <T> T create(Class<T> type);

  /**
   * 指定某个类型的构造函数构造
   *    参数1：该对象类型 class
   *    参数2：参数类型类型列表 class
   *    参数3：实际的参数列表 Object
   * Creates a new object with the specified constructor and params.
   *
   * @param <T>
   *          the generic type
   * @param type
   *          Object type
   * @param constructorArgTypes
   *          Constructor argument types
   * @param constructorArgs
   *          Constructor argument values
   * @return the t
   */
  <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

  /**
   * 该class 是不是一个集合类型
   * Returns true if this object can have a set of other objects.
   * It's main purpose is to support non-java.util.Collection objects like Scala collections.
   *
   * @param <T>
   *          the generic type
   * @param type
   *          Object type
   * @return whether it is a collection or not
   * @since 3.1.0
   */
  <T> boolean isCollection(Class<T> type);

}
