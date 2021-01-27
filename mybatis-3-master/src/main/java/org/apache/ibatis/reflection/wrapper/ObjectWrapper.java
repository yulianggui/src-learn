/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装功能，抽象了对象的属性信息
 *  对类级别的封装和处理，定义了一系列的属性查询和更新的方法
 *
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 根据属性分词器获取属性的值
   *
   * 如果 ObjectWrapper 包装的是普通的Bean 对象，则调用属性相应的getter 方法
   * 如果封装的是集合类，则获取指定key 或者下表对应的 value 值
   * @param prop 属性分词器
   * @return 返回值
   */
  Object get(PropertyTokenizer prop);

  /**
   * 根据属性分词器设置属性的值
   * 如果 ObjectWrapper 中封装的是普通的Bean 对象，则调用的相应属性的相应setter 方法
   * 如果封装的是集合类，则设置指定的key 或者下标对应的value 值
   * @param prop 属性分词器
   * @param value 需要设置的值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * 查找属性
   * @param name 属性名称
   * @param useCamelCaseMapping 是否为驼峰，如果为是，则 _ 被剔除掉
   * @return 返回属性 Reflection 中的 caseInsensitivePropertyMap 查找
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  String[] getGetterNames();

  String[] getSetterNames();

  Class<?> getSetterType(String name);

  Class<?> getGetterType(String name);

  boolean hasSetter(String name);

  boolean hasGetter(String name);

  /**
   * 为属性表达式指定的属性创建相应的 MetaObject 对象 未理解，待看实现类？？？
   * @param name 分词表达式中的某个属性名称
   * @param prop 属性分词器
   * @param objectFactory 对象工厂
   * @return 返回值
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  boolean isCollection();

  /**
   * 添加元素
   * @param element 节点
   */
  void add(Object element);

  /**
   * 批量添加
   * @param element 街道集合
   * @param <E> 泛型
   */
  <E> void addAll(List<E> element);

}
