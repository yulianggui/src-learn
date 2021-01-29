/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.logging.commons;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *    1、实现现有的 Log (Mybatis)
 *    2、持有Common 的Log
 *    3、调用 Log .error ，真正调用的是 Common.log 实现类的 log.error
 * 这里就是 CommonLogging 的包装
 * @author Clinton Begin
 */
public class JakartaCommonsLoggingImpl implements org.apache.ibatis.logging.Log {

  private final Log log;

  public JakartaCommonsLoggingImpl(String clazz) {
    log = LogFactory.getLog(clazz);
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  @Override
  public boolean isTraceEnabled() {
    return log.isTraceEnabled();
  }

  @Override
  public void error(String s, Throwable e) {
    log.error(s, e);
  }

  @Override
  public void error(String s) {
    log.error(s);
  }

  @Override
  public void debug(String s) {
    log.debug(s);
  }

  @Override
  public void trace(String s) {
    log.trace(s);
  }

  @Override
  public void warn(String s) {
    log.warn(s);
  }

}
