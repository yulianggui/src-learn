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
package org.apache.ibatis.reflection;

public interface ReflectorFactory {

  /**
   * 是否开启缓存
   * @return
   */
  boolean isClassCacheEnabled();

  /**
   * 设置是否缓存
   * @param classCacheEnabled
   */
  void setClassCacheEnabled(boolean classCacheEnabled);

  /**
   * 构建 Class 对应的 Reflector
   * @param type 类型，在Mybatis 中这里一般都算 JavaBean 的裂隙
   * @return 返回 Reflector
   */
  Reflector findForClass(Class<?> type);
}
