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
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * 对象属性信息功能
 * @author Clinton Begin
 */
public class MetaObject {

  /**
   * 原始属性
   */
  private final Object originalObject;
  /**
   * 对象包装器 -- 全是接口啊，设计的好啊
   */
  private final ObjectWrapper objectWrapper;
  /**
   * 对象工厂
   */
  private final ObjectFactory objectFactory;
  /**
   * 对象包装器工厂
   */
  private final ObjectWrapperFactory objectWrapperFactory;
  /**
   * 反射工具Reflector工厂
   */
  private final ReflectorFactory reflectorFactory;

  private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    this.originalObject = object;
    this.objectFactory = objectFactory;
    this.objectWrapperFactory = objectWrapperFactory;
    this.reflectorFactory = reflectorFactory;

    if (object instanceof ObjectWrapper) {
      // 如果object 本身就是一个包装器类型
      this.objectWrapper = (ObjectWrapper) object;
    } else if (objectWrapperFactory.hasWrapperFor(object)) {
      // 若 objectWrapperFactory 能够为该原始对象创建对应的ObjectWrapper 对象，则由优先
      // 使用objectWrapperFactory 工厂，而 DefaultObjectWrapperFactory.hasWrapperFor 始终返回
      // false 。 也就是说，这里除非用户自定义了 objectWrapperFactory 工厂，并且提供了 ObjectWrapper 的实现
      // 才会起作用。 用户自定义的 objectWrapperFactory 可以在 mybatis-config.xml 中进行覆盖默认配置
      this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
    } else if (object instanceof Map) {
      // 如果是一个Map
      this.objectWrapper = new MapWrapper(this, (Map) object);
    } else if (object instanceof Collection) {
      // 如果是集合。 集合类型仅仅支持 [] 方式、添加元素等操作，不支持 get | set 方法
      this.objectWrapper = new CollectionWrapper(this, (Collection) object);
    } else {
      // 否则认为是普通Bean 对象
      this.objectWrapper = new BeanWrapper(this, object);
    }
  }

  public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
    if (object == null) {
      return SystemMetaObject.NULL_META_OBJECT;
    } else {
      return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }
  }

  public ObjectFactory getObjectFactory() {
    return objectFactory;
  }

  public ObjectWrapperFactory getObjectWrapperFactory() {
    return objectWrapperFactory;
  }

  public ReflectorFactory getReflectorFactory() {
    return reflectorFactory;
  }

  public Object getOriginalObject() {
    return originalObject;
  }

  /**
   * 查找属性
   * @param propName 属性名称
   * @param useCamelCaseMapping 是否是否驼峰的方式，多处有描述过，不在重复
   * @return 返回值
   */
  public String findProperty(String propName, boolean useCamelCaseMapping) {
    // 注意，objectWrapper 中，传入的 MetaObject 对象，也就是说，在 objectWrapper 中，可以得到
    // ReflectorFactory 对应的 originalObject 的set|get 字段等这些信息
    return objectWrapper.findProperty(propName, useCamelCaseMapping);
  }

  public String[] getGetterNames() {
    return objectWrapper.getGetterNames();
  }

  public String[] getSetterNames() {
    return objectWrapper.getSetterNames();
  }

  public Class<?> getSetterType(String name) {
    return objectWrapper.getSetterType(name);
  }

  public Class<?> getGetterType(String name) {
    return objectWrapper.getGetterType(name);
  }

  public boolean hasSetter(String name) {
    return objectWrapper.hasSetter(name);
  }

  public boolean hasGetter(String name) {
    return objectWrapper.hasGetter(name);
  }

  /**
   * 获取属性值
   * 1、user ，直接走  objectWrapper.get(prop) 这个流程
   * 2、user.dept[0]
   *     第一步 prop.hasNext() ，返回再次会调用 getValue(user)
   *     第二步 收到 user，此时返回的是 user 这个对象
   *     第三步 MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
   *          得到 user 对应的 MetaObject
   *     第四步：此时得到 user 不为null，将 dept[0] 传过去，继续递归
   *          if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
   *              return null;
   *          } else {
   *              return metaValue.getValue(prop.getChildren());
   *          }
   *     第五步：
   *          不走 prop.hasNext()
   *          走 return objectWrapper.get(prop); 流程结束
   *
   *     // 写总结的时候，最好把执行流程图画出来
   *
   * @param name 属性名称
   * @return 返回值
   */
  public Object getValue(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      // 注意一下，这里可是将 indexName 传过去的
      // 处理属性解析器之后生成的第一个个属性，创建相应的 MetaObject 对象信息
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        return null;
      } else {
        return metaValue.getValue(prop.getChildren());
      }
    } else {
      return objectWrapper.get(prop);
    }
  }

  /**
   * 设置属性值
   * @param name 属性名称
   * @param value 属性值
   */
  public void setValue(String name, Object value) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        if (value == null) {
          // don't instantiate child path if value is null
          return;
        } else {
          // 如果是 SystemMetaObject.NULL_META_OBJECT 这个类型，说明没找到相应的属性set 方法
          // 那么此时会利用 objectWrapper.instantiatePropertyValue(name, prop, objectFactory); 创建一个metaObject
          // 这里有困惑？? 为啥找不到了，还需要生成一个呢？待回头学习啊
          // 属性名称对应的set|is 、字段找不到，但是 传过来的赋值对象又不为null ，那么就调用此方法
          metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
        }
      }
      // 将值设置进去
      metaValue.setValue(prop.getChildren(), value);
    } else {
      objectWrapper.set(prop, value);
    }
  }

  public MetaObject metaObjectForProperty(String name) {
    Object value = getValue(name);
    return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
  }

  public ObjectWrapper getObjectWrapper() {
    return objectWrapper;
  }

  public boolean isCollection() {
    return objectWrapper.isCollection();
  }

  public void add(Object element) {
    objectWrapper.add(element);
  }

  public <E> void addAll(List<E> list) {
    objectWrapper.addAll(list);
  }

}
