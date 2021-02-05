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
package org.apache.ibatis.builder.xml;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Frank D. Martinez [mnesarco]
 */
public class XMLIncludeTransformer {

  private final Configuration configuration;
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }

  /**
   *  这个解析要在 sql 标签之后  解析 include 标签
   *  将 include 节点替换成 sql 节点中定义的sql 片段，边将其中的 ${xxx} 占位符替换为真实的参数，该解析过程在
   * @param source
   */
  public void applyIncludes(Node source) {
    Properties variablesContext = new Properties();
    Properties configurationVariables = configuration.getVariables();
    /* 如果 configurationVariables != null ,则 variablesContext.pulAll(configurationVariables); */
    Optional.ofNullable(configurationVariables).ifPresent(variablesContext::putAll);
    // 真正的处理 include 节点  included 为 false ，此时传入的 source 为 select | insert | update | delete
    applyIncludes(source, variablesContext, false);
  }

  /**
   * 这个Node 可能是 select | update | insert | delete 节点
   * 可能是 include 节点
   *
   * 该解析过程可能会涉及多层递归 Mybatis 技术内幕  198 页
   * Recursively apply includes through all SQL fragments.
   *
   * @param source
   *          Include node in DOM tree
   * @param variablesContext
   *          Current context for static variables with values
   */
  private void applyIncludes(Node source, final Properties variablesContext, boolean included) {
    if ("include".equals(source.getNodeName())) {

      /**
       *   <include refid="id">
       *      <property name="" value=""/>
       *   </include>
       */
      // 找到 refid 指向的 sql
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);
      // 解析 property 属性，如果有必要，还要解析占位符
      // 返回的 toIncludeContext 为当前 include 中的加上 variablesContext 中的
      Properties toIncludeContext = getVariablesContext(source, variablesContext);
      // 继续递归，注意此时 included 为 true
      // 处理的是 toInclude ，的 sql , 这里会进入到 解析占位符的分支
      // 执行完这里之后  toInclude 的 sql 中的 文本，内嵌的 include 的文本，都已经被解析完毕 ${这种占位符}
      // cc ${a} 占位符解析不到则按照 cc ${a} 原样返回
      applyIncludes(toInclude, toIncludeContext, true);
      // 看下 sql 的 Node 和 include 是否为同一个 XML 中
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        // 不在，则将 toInclude 导入到 当前的 include 所在的 XML Document 中
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }

      // include 的父级 select | update | insert | delete
      // 将父 select | update | insert | delete Node中的 当前的 include 节点替换为 toInclude
      source.getParentNode().replaceChild(toInclude, source);
      // 如果 当前的 sql 还有子节点
      // 则 select | update | insert | delete Node 将会插入 toInclude.getFirstChild() 到 toInclude 之前

      // 删除当前的 toInclude，即sql 节点。请注意
      // 这里 source.getParentNode().replaceChild(toInclude, source); 已经替换掉了 <select> 中的 include ，即此
      // 出替换的作用是为了上面 这里做铺垫
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      // 已经不需要 toInclude（sql） 这节点了，此时 所有之前 select | insert | update | delete
      // 的 include 都将会被替换为 最终解析了 占位符 文本的某一端 sql|text ，因为可能还会有动态的标签
      toInclude.getParentNode().removeChild(toInclude);
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      // 注意，第一次进来是 select | .....，所有 所有的 include 标签都会被解析


      // 第二次估计就是 include refid 指定的 sql 标签

      // 这里处理元素
      // 遍历 当前SQL 语句的子节点  variablesContext
      // 如果此时 为 included 节点，并且 variablesContext size!=0

      //    <sql id="">
      //      <include refid=""/>
      //      <bind name="" value=""/>
      //    </sql>
      if (included && !variablesContext.isEmpty()) {
        // replace variables in attribute values
        // 解析占位符 ${}  node 的值 <include>value</include>
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
          Node attr = attributes.item(i);
          attr.setNodeValue(PropertyParser.parse(attr.getNodeValue(), variablesContext));
        }
      }
      // 遍历子节点
      NodeList children = source.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        // 比如 sql 中，可能会是 子的 include。文本节点等
        applyIncludes(children.item(i), variablesContext, included);
      }
    } else if (included && (source.getNodeType() == Node.TEXT_NODE || source.getNodeType() == Node.CDATA_SECTION_NODE)
        && !variablesContext.isEmpty()) {
      // 这里处理文本
      // replace variables in text node
      // 解析文本了
      source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
    }
  }

  private Node findSqlFragment(String refid, Properties variables) {
    refid = PropertyParser.parse(refid, variables);
    // currentNamespace.refid(fefid 不包含 . 的前提下)
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      // 深度克隆一个新的 XNode 节点返回
      return nodeToInclude.getNode().cloneNode(true);
    } catch (IllegalArgumentException e) {
      // 找不到待会再从新解析
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  private String getStringAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }

  /**
   * Read placeholders and their values from include node definition.
   *
   * @param node
   *          Include node instance
   * @param inheritedVariablesContext
   *          Current context used for replace variables in new variables values
   * @return variables context from include instance (no inherited values)
   */
  private Properties getVariablesContext(Node node, Properties inheritedVariablesContext) {
    Map<String, String> declaredProperties = null;
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        String name = getStringAttribute(n, "name");
        // Replace variables inside
        String value = PropertyParser.parse(getStringAttribute(n, "value"), inheritedVariablesContext);
        if (declaredProperties == null) {
          declaredProperties = new HashMap<>();
        }
        if (declaredProperties.put(name, value) != null) {
          throw new BuilderException("Variable " + name + " defined twice in the same include definition");
        }
      }
    }
    if (declaredProperties == null) {
      return inheritedVariablesContext;
    } else {
      Properties newProperties = new Properties();
      newProperties.putAll(inheritedVariablesContext);
      newProperties.putAll(declaredProperties);
      return newProperties;
    }
  }
}
