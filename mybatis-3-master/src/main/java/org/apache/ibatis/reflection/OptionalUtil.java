/**
 *    Copyright 2009-2018 the original author or authors.
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

import java.util.Optional;

/**
 * @deprecated Since 3.5.0, Will remove this class at future(next major version up).
 */
@Deprecated
public abstract class OptionalUtil {

  /**
   * 返回 Optional 对象
   * @param value 如果 value 为 null ,则new 一个对象，但是 this.value = null ,否则 Optional 中value= object value
   * @return
   */
  public static Object ofNullable(Object value) {
    return Optional.ofNullable(value);
  }

  private OptionalUtil() {
    super();
  }
}
