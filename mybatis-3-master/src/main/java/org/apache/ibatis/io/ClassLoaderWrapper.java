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
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * 类加载器 ClassLoader 的包装器
 *   其中包含多个ClassLoader 对象，通过调整多个类加载器的使用顺序，ClassLoaderWrapper 可以确保返回给系统使用
 *   的是正确的类加载器。使用ClassLoaderWrapper 就如同使用一个ClassLoader 对象，ClassLoaderWrapper 会按
 *   照指定的顺序依次检测其中封装的 ClassLoader 对象，并从中选取第一个可用的ClassLoader 完成相关功能
 *
 * ClassLoaderWrapper 的主要功能可以分为三大类：
 *   分别是：
 *     getResourceAsURL
 *     classForName
 *     getResourceAdStream
 *   三个方法经过解析逻辑之后，都会调用参数为String[] 的ClassLoader[] 的重载
 *
 *
 * A class to wrap access to multiple class loaders making them work as one
 *
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

  /**
   * 默认的类加载器，这个是用户指定的
   */
  ClassLoader defaultClassLoader;
  /**
   * 系统加载器， Application 类加载器
   * 即 classpath 类路径下的类加载器
   */
  ClassLoader systemClassLoader;

  ClassLoaderWrapper() {
    try {
      // 初始化字段，其实很简单，就是通过ClassLoader 拿到 SystemClassLoader 对象
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
      // AccessControlException on Google App Engine
    }
  }

  /**
   * Get a resource as a URL using the current class path
   *
   * @param resource - the resource to locate
   * @return the resource or null
   */
  public URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /**
   * Get a resource from the classpath, starting with a specific class loader
   *
   * @param resource    - the resource to find
   * @param classLoader - the first classloader to try
   * @return the stream or null
   */
  public URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /**
   * Get a resource from the classpath
   *
   * @param resource - the resource to find
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /**
   * Get a resource from the classpath, starting with a specific class loader
   *
   * @param resource    - the resource to find
   * @param classLoader - the first class loader to try
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /**
   * Find a class on the classpath (or die trying)
   *
   * @param name - the class to look for
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /**
   * Find a class on the classpath, starting with a specific classloader (or die trying)
   *
   * @param name        - the class to look for
   * @param classLoader - the first classloader to try
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /**
   * Try to get a resource from a group of classloaders
   *
   * @param resource    - the resource to get
   * @param classLoader - the classloaders to examine
   * @return the resource or null
   */
  InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
    for (ClassLoader cl : classLoader) {
      if (null != cl) {

        // try to find the resource as passed
        InputStream returnValue = cl.getResourceAsStream(resource);

        // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
        if (null == returnValue) {
          returnValue = cl.getResourceAsStream("/" + resource);
        }

        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /**
   * Get a resource as a URL using the current class path
   *
   * @param resource    - the resource to locate
   * @param classLoader - the class loaders to examine
   * @return the resource or null
   */
  URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

    URL url;

    /**
     * 遍历所有的类加载器进行加载，如果有一个成功解析到 url ，则返回了
     */
    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        // look for the resource as passed in...
        // 调用 ClassLoader.getResource() 方法查找指定的资源
        // 可以解析很多中协议，暂时没有深入了解
        url = cl.getResource(resource);

        // ...but some class loaders want this leading "/", so we'll add it
        // and try again if we didn't find the resource
        // 如果找不到，加上 / 继续找
        if (null == url) {
          url = cl.getResource("/" + resource);
        }

        // "It's always in the last place I look for it!"
        // ... because only an idiot would keep looking for it after finding it, so stop looking already.
        // 找到了就返回了
        if (null != url) {
          return url;
        }

      }

    }

    // didn't find it anywhere.
    return null;

  }

  /**
   * Attempt to load a class from a group of classloaders
   *
   * @param name        - the class to load
   * @param classLoader - the group of classloaders to examine
   * @return the class
   * @throws ClassNotFoundException - Remember the wisdom of Judge Smails: Well, the world needs ditch diggers, too.
   */
  Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {

    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        try {
          // 尝试加载类
          return Class.forName(name, true, cl);

        } catch (ClassNotFoundException e) {
          // we'll ignore this until all classloaders fail to locate the class
        }

      }

    }

    throw new ClassNotFoundException("Cannot find class: " + name);

  }

  /**
   * 返回所有的类加载器
   * 1、参数传入的
   * 2、defaultClassLoader
   * 3、当前现成上下文的类加载器
   * 4、加载当前class 的类加载器
   * 5、classpath 类加载器
   *
   * @param classLoader 参数传入的类加载器
   * @return 返回所有的类加载器
   */
  ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    return new ClassLoader[]{
        classLoader,
        defaultClassLoader,
        Thread.currentThread().getContextClassLoader(),
        getClass().getClassLoader(),
        systemClassLoader};
  }

}
