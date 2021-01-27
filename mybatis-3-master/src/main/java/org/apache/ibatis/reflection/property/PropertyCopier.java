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
package org.apache.ibatis.reflection.property;

import java.lang.reflect.Field;

import org.apache.ibatis.reflection.Reflector;

/**
 * 属性拷贝器
 * @author Clinton Begin
 */
public final class PropertyCopier {

  private PropertyCopier() {
    // Prevent Instantiation of Static Class
  }

  /**
   * 属性返回值
   * 源、dest 需要时 type 的子或者子类
   * 相同类型，不同类型不能用
   * @param type 指定类型
   * @param sourceBean 源对象
   * @param destinationBean 目标对象
   */
  public static void copyBeanProperties(Class<?> type, Object sourceBean, Object destinationBean) {
    // 循环，从当前类开始，不断复制到父类，直到父类不存在
    Class<?> parent = type;
    while (parent != null) {
      // 所有声明的字段，本类中字段
      final Field[] fields = parent.getDeclaredFields();
      for (Field field : fields) {
        try {
          try {
            // 设置字段
            field.set(destinationBean, field.get(sourceBean));
          } catch (IllegalAccessException e) {
            // 先询问是否有权限
            if (Reflector.canControlMemberAccessible()) {
              field.setAccessible(true);
              field.set(destinationBean, field.get(sourceBean));
            } else {
              throw e;
            }
          }
        } catch (Exception e) {
          // Nothing useful to do, will only fail on final fields, which will be ignored.
          // final 类型的字段将会被忽略，不会进行处理
        }
      }
      // 找父类的
      parent = parent.getSuperclass();
    }
  }

}
