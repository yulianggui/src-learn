/**
 *    Copyright 2009-2016 the original author or authors.
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

import org.apache.ibatis.reflection.MetaObject;

/**
 * 也就是说，这里除非用户自定义了 objectWrapperFactory 工厂，并且提供了 ObjectWrapper 的实现
 * 才会起作用。 用户自定义的 objectWrapperFactory 可以在 mybatis-config.xml 中进行覆盖默认配置
 * 要不然这两个方法其实不起作用，在MeteObject 中的构造方法可以看出来用法
 * @author Clinton Begin
 */
public interface ObjectWrapperFactory {

  /**
   * 默认的实现 DefaultObjectWrapperFactory 返回false
   * @param object 对象信息
   * @return 返回
   */
  boolean hasWrapperFor(Object object);

  /**
   * 获取对象的包装类型。 DefaultObjectWrapperFactory 抛出异常
   * @param metaObject 对象新
   * @param object 对象
   * @return 返回值
   */
  ObjectWrapper getWrapperFor(MetaObject metaObject, Object object);

}
