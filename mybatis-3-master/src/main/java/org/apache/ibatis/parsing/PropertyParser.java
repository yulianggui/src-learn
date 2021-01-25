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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * 仅仅解析 ${} 类型的占位符
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  /**
   * 属性前缀
   */
  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * 指示是否在占位符上启用默认值的特殊属性键，留个外部进行配置覆盖的 key
   * 默认为 false 不启用
   * 如果你把它设置为启用，则可以编写默认值
   *
   * 可以在mybatis-config.xml 或者在 创建 sqlSessionFactory 的时候，设置启用
   * <properties>
   *   <property name="org.apache.ibatis.parsing.PropertyParser.enable-default-value" value="true"/>
   * </properties>
   *
   * The special property key that indicate whether enable a default value on placeholder.
   * <p>
   *   The default value is {@code false} (indicate disable a default value on placeholder)
   *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * 启用默认占位符功能时，分割符号配置的 key
   *
   * 例如
   * ${jdbc.driver:com.mysql.jdbc.Driver}
   * 此处如果要将 : 替换为其他占位符 ,
   *   org.apache.ibatis.parsing.PropertyParser.default-value-separator=,
   * <properties>
   *   <property name="org.apache.ibatis.parsing.PropertyParser.default-value-separator" value=","/>
   * </properties>
   *
   * The special property key that specify a separator for key and default value on placeholder.
   * <p>
   *   The default separator is {@code ":"}.
   * </p>
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";
  /**
   启用占位符默认值，默认为 false
   */
  private static final String ENABLE_DEFAULT_VALUE = "false";
  /**
   默认占位符分割符号为 :
   */
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  /**
   * 仅仅解析 ${} 形式的占位符，不解析 #{}
   * @param string 需要解析的占位符字符串 ， 例如 ${key} ${key:adf}  select * from ${tableName:user}
   * @param variables 属性 key - value
   * @return 返回解析后的 字符串
   */
  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  /**
   * 解析 userName:ylg 占位符
   * handleToken ，如果集合variables==null 集合不存在，返回 ${content}
   */
  private static class VariableTokenHandler implements TokenHandler {
    /**
     * 构造参数传入的 属性集合
     */
    private final Properties variables;
    /**
     * 是否启用占位符默认值功能
     */
    private final boolean enableDefaultValue;
    /**
     * 如果启用了占位符默认值功能，此时表达式中 key 和默认值的 分隔符
     */
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    /**
     * 获取variables 中 key 对应的值
     * @param key key
     * @param defaultValue 获取不到时返回的值
     * @return 返回值
     */
    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    @Override
    public String handleToken(String content) {
      // demo==>  jdbc.url:com.mysql.jdbc.Driver

      if (variables != null) {
        // key 需要解析的字符串内容，可能包含需要解析的占位符信息
        String key = content;
        // 是否启用占位符默认值功能
        if (enableDefaultValue) {
          // 进入启用占位符默认值的分支
          // 得到占位符分割和的 索引
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;

          // 如果分割符存在，即可能配置了默认值
          if (separatorIndex >= 0) {
            // 得到 variables key=jdbc.url
            key = content.substring(0, separatorIndex);
            // 得到 默认值为 com.mysql.jdbc.Driver （key 的索引+长度，得到默认值字符在文本中的索引）
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          // 如果默认值不为null ，则去查看是否在 variables 进行了设置，不然就使用 defaultValue
          // 如果 defaultValue == null ，并且  variables 存在则返回，否则 返回 ${content}
          if (defaultValue != null) {
            return variables.getProperty(key, defaultValue);
          }
        }
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      // 如果集合不存在，返回 ${content}
      return "${" + content + "}";
    }
  }

}
