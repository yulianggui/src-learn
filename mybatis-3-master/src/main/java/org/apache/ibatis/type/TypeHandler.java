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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 类型转换接口
 *  将javaType 转为 jdbcType
 *  将jdbcType 转为 javaType
 * @author Clinton Begin
 */
public interface TypeHandler<T> {

  /**
   * 通过 PreparedStatement 为SQL 语句绑定参数， 会将数据有 jdbcType 类型转换为 Java 类型
   * @param ps 预编译信息
   * @param i 参数位置
   * @param parameter 参数 -- 泛型
   * @param jdbcType jdbc类型
   * @throws SQLException 异常
   */
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * Gets the result.
   * 从返回结果中获取结果信息 ResultSet
   * @param rs
   *          the rs
   * @param columnName
   *          Colunm name, when configuration <code>useColumnLabel</code> is <code>false</code>
   * @return the result
   * @throws SQLException
   *           the SQL exception
   */
  T getResult(ResultSet rs, String columnName) throws SQLException;

  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
