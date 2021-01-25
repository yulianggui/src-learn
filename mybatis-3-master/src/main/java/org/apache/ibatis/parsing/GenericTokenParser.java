/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.parsing;

/**
 * 一般意义上的字符串解析
 *   只有一个 parse 的对外方法
 *   将 形如 select * from ${tableName} 的形式解析为 select * from user
 *   ${ == openToken
 *   } == closeToken
 *   key==tableName, 此时的 value 取决于 TokenHandler（比如测试类中的，简单直接作为key，取map 中的value）
 *   或者 org.apache.ibatis.parsing.GenericTokenParser 中，作为 jdbc.url:com.mysql.jdbc.Driver 解析
 *   可配置启用占位符默认值的功能
 * @author Clinton Begin
 */
public class GenericTokenParser {

  /**
   * 占位符开始，比如 ${  #{
   */
  private final String openToken;
  /**
   * 占位符结束，比如 }
   */
  private final String closeToken;
  /**
   * 解析占位符 userName:ylg 形式
   */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  /**
   * 顺序查找 openToken 和 closeToken ，得到 需要解析的占位符中的key ，并交给 TokenHandler 解析，其他不是站位符的部分原样返回
   * @param text 需要解析的文本
   * @return 返回解析占位符之后的文本字符串
   */
  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    // 去掉转移字符

    // search open token
    // 例如找到 ${ 开始的索引，没找到直接返回 text，还有一个  indexOf(str, 0) 方法
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }
    // 转为字符数组？为啥要转为数组？
    char[] src = text.toCharArray();
    // 偏移量
    int offset = 0;
    // 解析后的字符串
    final StringBuilder builder = new StringBuilder();
    // 记录占位符字符串，比如 ${jdbc.url} ==> expression==jdbc.url
    StringBuilder expression = null;
    do {
      // 如果 ${ 之前为转义字符，跳过转移字符，并将 openToken 加入到 builder
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);
        // offset 偏移量推进
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // 添加占位符之前的信息
        builder.append(src, offset, start - offset);
        // 偏移量推进
        offset = start + openToken.length();
        // 找到最近一个 占位符索引
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          // 如果 end 在偏移量之后，并且为 转移字符反斜杠 \
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            // expression = }
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            // 往后找 } 的索引，这里不会出现 }user.name} 的情况吗？ 有可能会，但是直接解析为 表达式的一部分
            end = text.indexOf(closeToken, offset);
          } else {
            // 否则 select * ${.append('userName}')
            // expression 用来获取 ${ .. } 之间的内容，即 key ）
            expression.append(src, offset, end - offset);
            break;
          }
        }
        if (end == -1) {
          // close token was not found.
          // 没找到 ${ 对应的}
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // end >= 0
          // 将表到式中的字符串，利用 占位符处理器直接解析 比如： org.apache.ibatis.parsing.GenericTokenParser ，解析 jdbc.url:userName ，切割 ：
          // 或者可以直接将 expression.toString() 作为 key , 从代Map|properties 属性信息中提取 value
          builder.append(handler.handleToken(expression.toString()));
          // 再次偏移
          offset = end + closeToken.length();
        }
      }
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }

}
