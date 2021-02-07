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
package org.apache.ibatis.scripting.xmltags;

/**
 * 根据 context 传入的数据进行 动态解析，参数解析改SQLNode 所记录的 动态 SQL 节点，并调用 DynamicContext.appendSql 方法
 * 将解析后的 SQL 片段追加到 DynamicContext.sqlBuilder 字段中
 * 当 SQL 节点下的所有 SQLNode 完成解析后，我们就可以从 DynamicContext 中获取一条动态生成的、完成的 SQL 语句
 * @author Clinton Begin
 */
public interface SqlNode {
  boolean apply(DynamicContext context);
}
