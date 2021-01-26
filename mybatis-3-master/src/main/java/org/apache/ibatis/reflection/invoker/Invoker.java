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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * 方法调用，字段赋值，二义性属性方法的调用，获取返回值的封装
 *  -- 也很值得参考学习啊
 * @author Clinton Begin
 */
public interface Invoker {
  /**
   * 调用 比如调用set方法，字段的赋值，获取等
   * @param target 目标对象
   * @param args 参数信息
   * @return 返回值 Object 如何转换赋值？ 估计是ObjectFactory 或者强转吧
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  Class<?> getType();
}
