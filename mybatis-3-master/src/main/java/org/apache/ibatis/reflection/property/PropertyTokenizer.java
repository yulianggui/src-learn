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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  private String name;
  private final String indexedName;
  private String index;
  private final String children;

  /**
   * 1、user.dept
   * 2、user.dept[0].name 两种情况。index=0
   * 3、map[key]，则index=key
   * 属性全名称解析
   * @param fullname 全名称
   */
  public PropertyTokenizer(String fullname) {
    // 第一个 .
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      // 名字
      name = fullname.substring(0, delim);
      // 子节点名称，后续可能还是复合型
      children = fullname.substring(delim + 1);
    } else {
      // 否则没有子节点
      name = fullname;
      children = null;
    }
    // index+name 即比如 name[0]  name=name index=0
    // map[id] name=map,index=id
    indexedName = name;
    // 索引
    delim = name.indexOf('[');
    if (delim > -1) {
      // 得到索引的值，比如 name[0] 为 0
      index = name.substring(delim + 1, name.length() - 1);
      // 得到[] 前面的名称， 比如name
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  /**
   * 解析下一个节点
   * @return 返回值。这里很巧妙啊，多看源码果然是有好处的
   */
  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
