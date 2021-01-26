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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * 反射信息，在mybatis 中，一个Reflector 缓存的是一个对象（JavaBean）：
 *    具备缓存的功能
 *    通过属性名和set/get 方法进行缓存
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 保存对象的Class 对象引用
   * 注意这里用 Class<?>
   */
  private final Class<?> type;
  /**
   * 可读属性名称数组； 在Mybatis 中定义为 存在getXXX 的方法名称  会把解析为属性
   */
  private final String[] readablePropertyNames;
  /**
   * 可写属性名称数组，同理。 setXXX
   */
  private final String[] writablePropertyNames;
  /**
   * 方法，属性的setXXX 方法的缓存， key 为方法名称 Invoker 是对Method 的封装
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();
  /**
   * 同理 getXXX 方法的缓存
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();
  /**
   * key 方法名称。 value getXXX 方法参数属性类型 Class 类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  /**
   * key 方法名称。 value setXXX 方法参数属性类型 Class 类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  /**
   * 记录默认的构造方法
   */
  private Constructor<?> defaultConstructor;
  /**
   * 记录所有set/get 方法名称的集合
   * key - value
   * 属性名称转大写   属性名称
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 记录当前用到反射操作的 Class 类型
    type = clazz;
    // 添加默认的构造方法
    addDefaultConstructor(clazz);
    // 解析clazz 中所有 getXXX 方法并存放到 getMethods 、getTypes 的缓存中
    addGetMethods(clazz);
    // 通上，解析的是set 方法，存放到 setMethods 、setTypes 中
    addSetMethods(clazz);
    // 解析 字段，如果 setMethods、getMethods、setTypes、getTypes 中，前提是前面两个方法中没有添加
    // 实际上是对 setMethods、getMethods 的补充吧，有些成员变量没有实现 setter|getter
    // 字段也通过 Invoke 进行了封装。
    addFields(clazz);
    // 可读属性名称，即get 方法（可能包括属性）的
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 可写数学名称，即set 方法（可能包括属性）的
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    // 这些属性转名称转为大写作为key ,在 caseInsensitivePropertyMap 中存一份
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 找到默认的构造方法。保存到成员变量
   * @param clazz clazz 类型
   */
  private void addDefaultConstructor(Class<?> clazz) {
    // 得到所有构造方法
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    // 找到参数为 0 个的构造方法，赋值给成员变量 。 使用jdk 1.8 的语法
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  /**
   * 找到所有的getXXX 方法。 并且是没有
   * @param clazz clazz 类型
   */
  private void addGetMethods(Class<?> clazz) {
    // 同一个setOrg 可能有多个重载； 或参数个数不一样，或参数个数一样，但是方法签名（参数类型）不一样
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 得到所有的方法列表 -- 包括 父类、接口定义的、父接口； 共有、私有、静态等方法。 通过方法签名进行去重
    Method[] methods = getClassMethods(clazz);
    // 找出只有方法参数为0，且 已get 开头的方法，将满足条件的存放到Map<String, List<Method>> 中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决get 属性名和方法的冲突信息。 当子类覆盖了父类中的方法时，就会存在同一个属性多个Method 的场景
    // 并且签名可能是不同的。 比如 A 有个方法 List<String> getName() ， 而B extend A 有个方法 ArrayList<String> getName(); 这种情况是可能存在的
    // 或者 isSuccess | getSuccess
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 解决 子类覆盖父类，但是前面不一致的场景，选出属性名称需要调用的 Mehtod
   * @param conflictingGetters 属性名称、List<Method> 的映射
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历Map 集合
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // winner 记录获胜者，即最终被挑选处理作为 属性调用的 getter 方法
      Method winner = null;
      // 属性名称
      String propName = entry.getKey();
      // 是否是模糊的，模糊的即代表不确定性 比如同时存在 isSuccess 和 getSuccess
      // 存在属性名一直但是返回类型不一样且没有继承|实现 的关系，即存在二义性，而在Mybatis 3.4 （包含）之前的版本会直接抛出异常
      // 新版进行了优化，在 addGetMethod 中，如果  isAmbiguous 为true ，将其封装到为 org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker
      // 暂时不会抛出异常
      boolean isAmbiguous = false;
      // 遍历所有候选者
      for (Method candidate : entry.getValue()) {
        // 第一个放行
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // winner 的返回类型
        Class<?> winnerType = winner.getReturnType();
        // 下一个候选者的返回类型
        Class<?> candidateType = candidate.getReturnType();
        // 如果当前的 winner 和下一个候选者的 返回类型一致， 返回类型相同可能是 isSuccess | getSuccess 同时存在
        if (candidateType.equals(winnerType)) {
          // 并且下一个候选者的返回类型不为 boolean ，直接退出来，此时 winner 胜出
          // 等于先找到先返回
          if (!boolean.class.equals(candidateType)) {
            // 旧版本抛出异常
            isAmbiguous = true;
            break;
          // 否则如果是 is 开头的方法名称，则优先选择 这个下一个候选者，然后继续往对比
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        // 如果下一个候选者返回的类型是否是 winnerType 的父类   A.isAssignableFrom(B) 判断A是否为B的父类
        /*
           class1.isAssignableFrom(class2) 判定此 Class 对象所表示的类或接口与指定的 Class 参数所表示的类或接口是否相同，或是否是其超类或超接口。
           如果是则返回 true；否则返回 false。如果该 Class表示一个基本类型，且指定的 Class 参数正是该 Class 对象，则该方法返回 true；否则返回 false

           class2是不是class1的子类或者子接口
           class1 是不是 class2 的父类

         */
        // 优先选择子类的返回类型
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          // 如果 winnerType 是 candidateType 的父类，优先选择子类的返回类型
          winner = candidate;
        } else {
          // 如果 winner 和 candidate 既不是同一个返回类型，也不是基础|实现的关心，则返回第一个 winner 了。 说明此时存在二义性，即模糊性
          // 旧版本抛出异常
          isAmbiguous = true;
          break;
        }
      }
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  /**
   * 属性名称保存到本类的 getMethods 和 getTypes 属性中
   * @param name 属性国民查
   * @param method 方法 get | is 方法
   * @param isAmbiguous 是否存在二义性
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 如果存在二义性，将 method 封装为 AmbiguousMethodInvoker ，否则封装为 MethodInvoker
    // 这两个均是Mybatis 对 Method 的封装
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    // 存放到getMethods 的Map 中
    getMethods.put(name, invoker);
    // 获取返回值的 Type ，在Java 中，所有的Methods ，包括参数类型，反射类型，字段Class 类型等都会实现Type 这个接口，作为一中声明
    // 参考 typeToClass 中的分支逻辑。 解析返回类型
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    // typeToClass(type) 获取对应的Class 对象-- 其实Hutool 源码中也要类似的处理逻辑
    // 找到返回值的类型就对了，比较难理解，这个还需要持续学习反射的知识点
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 以 name 为key 、 method Map为List<Method> 的item 添加到 conflictingMethods
   * 这里的name 为 get/set 后面的熟悉，转化为首字母小写的名称
   * @param conflictingMethods 集合
   * @param name setXX getXX 解析出来的属性名称
   * @param method 方法
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      // 如果key 对应的List<Method> 不存在，则新建一个List 返回，如果存在，则返回get(Key)中的
      // 自己的编码可以参考这样设计，不过对于不熟悉 jdk8 语法和使用的，看起来会有点费脑
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      // 将method 存放进去
      list.add(method);
    }
  }

  /**
   * 解析突出的Setters 方法合集  conflictingSetters 中的set 属性名称 和 List<Method> 是存在冲突的
   * @param conflictingSetters 属性名称、方法集合的映射信息
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 遍历
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      // 属性名称
      String propName = entry.getKey();
      // 同一个属性名称对应的方法合集
      List<Method> setters = entry.getValue();
      // 获取属性的Class 类型，get 中是否存在相应的属性返回值 -- 保证如果也存在相应的set，  get的返回值类型 和 set的参数类型应该是要有效选择一致的
      Class<?> getterType = getTypes.get(propName);
      // 是否是二义性的getXXX 方法
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      // set 方法是否存在二义性
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          // 如果 isGetterAmbiguous 不存在二义性，并且setter 方法的第一个参数行了和 get 的返回参数类型一直
          // 直接找到了
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          // 如果当前还没有存在二义性，找到一个更好的 setter 方法
          match = pickBetterSetter(match, setter, propName);
          // 如果找不到，则说明存在二义性 可能set|is 方法并没有提供，此时如果返回 null，则
          // isSetterAmbiguous = true，此时此段逻辑在for 循环中不会再次被调用
          // 出现此种情况，只能指望 第一个if 的逻辑中找到了


          // 这里也是值得学习的地方，
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        // 添加到 setMethods 和 setTypes
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 挑选出 pick 一个比较好的 setter 方法
   * @param setter1 当前指定的set 方法
   * @param setter2 下一个候选者，用来跟 setter1 比较
   * @param property 属性名称
   * @return 返回method
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    // 如果当前还没有选择中，则返回下一个候选者
    if (setter1 == null) {
      return setter2;
    }
    // 当前被选择者 set|is 方法的参数类型
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    // 候选者set | is 方法的参数类型
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 如果 paramType1 是 paramType2 的父类，则优先选择子类的
    // 否则反转一下
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 当前的查找不存在，即存在二义性了
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    // 先将 属性setter2 的方法声明为 二义性的 MethodInvoker，并将 Method1 和 Method2 的参数名称也携带进去
    setMethods.put(property, invoker);
    // 解析set 返回参数类型
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  /**
   * 这块会比较抽象
   * https://blog.csdn.net/max_value/article/details/105041666
   *
   *    类型存在嵌套。比如：
   *    List<String>外层是ParameterizedType类型，里面又是Class类型。
   *    List<T>外层是ParameterizedType类型，里面是TypeVariable类型。
   *    List<T>[]外层是GenericArrayType类型，里面是ParameterizedType类型，再里面是TypeVariable类型。
   *    List<? extends Map>外层是ParameterizedType类型，里面是WildcardType类型。
   *    根据 java.lang.Type 解析出Class 类型
   * @param src 传入的 type
   * @return 返回其Class 类型
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 如果是 Class ，直接返回类
    if (src instanceof Class) {
      result = (Class<?>) src;
    // 泛型类型，比如 A<T> List<String>
    } else if (src instanceof ParameterizedType) {
      // 返回真是的反射类型的值
      // ParameterizedType：泛型类型，如Map<String,String>,List<String>。getRawType返回原始类型，如List<String>中的List
      // 返回Map 、List
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      // 泛型数组类型,元素类型需要是ParameterizedType或TypeVariable，如List<T>[]、T[]、List<String>[]
      // getGenericComponentType返回数值元素的类型，如List<String>[]为List<String>即 ParameterizedType
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 如果为Class ，则返回一个数组类型 new [Class<?>]
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        // 否则可能是 ParameterizedType或TypeVariable，继续解析
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      // 否则返回Object
      result = Object.class;
    }
    return result;
  }

  /**
   * 添加字段
   * @param clazz 类型新
   */
  private void addFields(Class<?> clazz) {
    // 本类中 声明的所以 字段信息
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      //如果 setMethods 中的 key 不存在
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        // 获取类型， public | private | static final 等
        int modifiers = field.getModifiers();
        // 不是final 且不是 modifiers final 和 statics 不能进行变更
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // getMethods 不存在时
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 获取父类字段新 -- 这里不需要处理接口的，接口如果存在成员变量，那么必然是 statics final
    if (clazz.getSuperclass() != null) {
      // 相当于递归父类中的字段信息
      addFields(clazz.getSuperclass());
    }
  }

  /**
   * 添加setFiledId 方法 到 setMethods、setTypes
   * @param field 字段信息
   */
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      // 包装为 SetFieldInvoker 类型
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      // 解析字段类型
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      // 这里，如果 setXXX 中的 Type 跟字段的Type 不一样的话，优先选择字段的类型 ？
      // 个人认为是这样的，可以通过debug 测试一下
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 判断是否为有效的属性名称
   * @param name 非serialVersionUID、class、已 $ 开始的属性
   * @return 返回
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * 返回clazz 类型的所有方法。包括在 这个class 声明的，或者是起 父类中声明的
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    // 如果
    while (currentClass != null && currentClass != Object.class) {
      // 返回当前 class 的所有共有、非共有、公共、保护、默认（包）访问和私有方法，但是不包括父类中的方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 如果定义的接口，这遍历接口集合，将接口当前层级的 公有方法添加进来
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 指向父类，然后进行同样的操作
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    // 转换为数组
    return methods.toArray(new Method[0]);
  }

  /**
   * https://blog.csdn.net/qq_40272978/article/details/107370187
   * uniqueMethods 保存方法的Map
   * @param uniqueMethods 唯一方法
   * @param methods 需要添加到 uniqueMethods 的方法数组
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 是否是桥接方法
      // 桥接方法是 JDK 1.5 引入泛型后，为了使Java的泛型方法生成的字节码和 1.5 版本前的字节码相兼容而实现的。具体作用
      // 在于判断方法是否是有编译成在编译阶段自动生成的。
      if (!currentMethod.isBridge()) {
        // 获取该方法的签名，签名使得方法签名唯一
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  /**
   * Method 方法签名
   *
   * 放回类型#方法名称
   * returnType.getName()#method.getName():parameters[0].getName(),parameters[1].getName()
   *
   * @param method 方法Method 对象
   * @return 返回签名
   */
  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 返回类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * 核查是否可以控制到成员访问权限先， 是否开了安全信息
   *   -- SecurityManager 这个目前个人对这个安全的概念比较模糊
   *   -- 待以后的学习中再了解，此处简单理解为jvm 是否开启了较强的安全规则
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
