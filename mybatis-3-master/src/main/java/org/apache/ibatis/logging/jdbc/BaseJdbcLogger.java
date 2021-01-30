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
package org.apache.ibatis.logging.jdbc;

import java.lang.reflect.Method;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ArrayUtil;

/**
 * Base class for proxies to do logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public abstract class BaseJdbcLogger {

  /**
   * 用来记录绑定SQL 参数相关的 set*() 方法名称
   * 记录例了 PreparedStatement 接口中定义的常用的set*() 方法
   */
  protected static final Set<String> SET_METHODS;
  /**
   * 执行SQL 语句相关方法的名称
   * 记录了Statement 接口和PreparedStatement 接口中与执行SQL 语句相关的方法
   */
  protected static final Set<String> EXECUTE_METHODS = new HashSet<>();

  /**
   * 其实就是记录了
   * 字段映射通过 PreparedStatement 方法set* 设置的键值对
   */
  private final Map<Object, Object> columnMap = new HashMap<>();

  /**
   * 字段key 的名称
   * PreparedStatement 方法set* 设置的key 值
   */
  private final List<Object> columnNames = new ArrayList<>();
  /**
   * 字段值
   * PreparedStatement 方法set* 设置的value 值
   */
  private final List<Object> columnValues = new ArrayList<>();

  /**
   * 持有Mybatis 中的Log
   */
  protected final Log statementLog;
  /**
   * 查询栈深度
   * 即，记录了SQL 的层数，用于格式化输出SQL
   */
  protected final int queryStack;

  /**
   * 默认的构造方法
   * Default constructor
   */
  public BaseJdbcLogger(Log log, int queryStack) {
    this.statementLog = log;
    // 查询栈为 0 ，什么意思？？ ?
    if (queryStack == 0) {
      this.queryStack = 1;
    } else {
      this.queryStack = queryStack;
    }
  }

  static {
    /**
     * 1、获取 PreparedStatement 中的所有声明的方法
     * 2、过滤掉这些方法，只保留 set 开头的方法
     * 3、并且只保留参数大于1 的方法，即set 参数设置
      */
    SET_METHODS = Arrays.stream(PreparedStatement.class.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("set"))
            .filter(method -> method.getParameterCount() > 1)
            .map(Method::getName)
            .collect(Collectors.toSet());

    /**
     * 记录执行的放放
     * Statement 中
     * 1、execute、executeUpdate、executeQuery、addBatch 这些方法
     */
    EXECUTE_METHODS.add("execute");
    EXECUTE_METHODS.add("executeUpdate");
    EXECUTE_METHODS.add("executeQuery");
    EXECUTE_METHODS.add("addBatch");
  }

  /**
   * 添加字段映射
   * @param key 字段key
   * @param value 字段 key 对应的值
   */
  protected void setColumn(Object key, Object value) {
    // 字段key
    columnMap.put(key, value);
    // key 名称
    columnNames.add(key);
    // 字段key 对应的值
    columnValues.add(value);
  }

  /**
   * 获取字段key 对应的值
   * @param key 字段key
   * @return 返回 字段key 对应的值
   */
  protected Object getColumn(Object key) {
    return columnMap.get(key);
  }

  /**
   * 获取参数值
   * @return 返回
   */
  protected String getParameterValueString() {
    // 数组columnValues 转List
    List<Object> typeList = new ArrayList<>(columnValues.size());
    // 遍历字段值
    for (Object value : columnValues) {
      // 如果为 空，返回 null 字符串。 注意这里是 日志类
      if (value == null) {
        typeList.add("null");
      } else {
        // toString(value) + ( + 类行简称 )
        // 比如： value = hello
        // hello(String) -- 这就是平时我们看到的参数列表信息中传入的参数值的那一行
        typeList.add(objectValueString(value) + "(" + value.getClass().getSimpleName() + ")");
      }
    }
    // 转String
    final String parameters = typeList.toString();
    // 这里是 List ，将 [hello(String), 1(Integer)] 中的第一和最后一个字符去掉
    return parameters.substring(1, parameters.length() - 1);
  }

  /**
   * 获取 values 对应的字符串输出
   *   其实这里只是对数组类型的参数进行了处理
   *      而这里就使用到了之前看到的 ArrayUtil 这个工具类
   * @param value 值
   * @return 返回值
   */
  protected String objectValueString(Object value) {
    if (value instanceof Array) {
      try {
        return ArrayUtil.toString(((Array) value).getArray());
      } catch (SQLException e) {
        return value.toString();
      }
    }
    // 不是数据，调用value 原生的 toString 方法即可
    return value.toString();
  }

  protected String getColumnString() {
    return columnNames.toString();
  }

  /**
   * 清空字段信息
   */
  protected void clearColumnInfo() {
    columnMap.clear();
    columnNames.clear();
    columnValues.clear();
  }

  /**
   * 删除空额外的空格
   * @param original 原始的字符串
   * @return 返回值
   */
  protected String removeExtraWhitespace(String original) {
    return SqlSourceBuilder.removeExtraWhitespaces(original);
  }

  protected boolean isDebugEnabled() {
    return statementLog.isDebugEnabled();
  }

  protected boolean isTraceEnabled() {
    return statementLog.isTraceEnabled();
  }

  protected void debug(String text, boolean input) {
    if (statementLog.isDebugEnabled()) {
      statementLog.debug(prefix(input) + text);
    }
  }

  protected void trace(String text, boolean input) {
    if (statementLog.isTraceEnabled()) {
      statementLog.trace(prefix(input) + text);
    }
  }

  /**
   * isInput 为 true | false
   * ========== >select * from user where id = ?
   * ========== <
   * queryStack 为了层次输出日志
   *    queryStack = 1
   *         == >select * from user where id = ?
   *    queryStack = 2
   *         ==== >select * from sub_super where user_id = ?
   * 如果inInput 为 true
   * @param isInput 是否未 true
   * @return 返回字符串
   */
  private String prefix(boolean isInput) {
    // sql 层次的深度
    char[] buffer = new char[queryStack * 2 + 2];
    // 将 buffer 用 = 字符填充
    Arrays.fill(buffer, '=');
    // 倒数第二个字符 = 设置为 ‘ ’ 空
    buffer[queryStack * 2 + 1] = ' ';
    // 如果是输入，则最后一个个 >
    if (isInput) {
      buffer[queryStack * 2] = '>';
    } else {
      // 否则为 <
      buffer[0] = '<';
    }
    return new String(buffer);
  }

}
