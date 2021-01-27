package org.apache.ibatis.reflection.property;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertyCopierTest {

  @Test
  public void test(){

    TestProB testProB = new TestProB();
    //testProB.setId("12323");
    testProB.setName("张三");

    TestProB testProA = new TestProB();
    PropertyCopier.copyBeanProperties(TestProB.class, testProB, testProA);

    System.out.println(testProA.getId());
    System.out.println(testProA.getName());

  }

}
