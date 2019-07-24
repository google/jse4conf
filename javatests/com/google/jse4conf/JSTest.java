// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.jse4conf;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mozilla.javascript.Scriptable;

@RunWith(JUnit4.class)
public class JSTest extends TestBase {

  @Test
  public void sanity() throws Exception {
    // The last JS statement's value is returned and checked.
    js.check("x=3+5;x+100;", 108);
  }

  @Test
  public void multiInitCalls() {
    evalJS("x=10;y=20;");
    js.check("x+y", 30);
    js.check("x*y", 200);
    js.reset("x=1");
    js.check("x", 1);
    js.check("y", JS.rhinoUndefinedError("y", "<str>", 1));
  }

  @Test
  public void scriptTypes() {
    assertNull(evalJS("x=null")); // JavaScript null evals to Java null
    js.checkTypes("3>5", "Boolean", "java.lang.Boolean");

    // Rhino returns Integer if possible, otherwise a Double.
    js.checkTypes("12500", "Number", "java.lang.Integer");
    js.checkTypes("125.00", "Number", "java.lang.Integer");
    js.checkTypes("1.25", "Number", "java.lang.Double");
    assertEquals(12500, JS.toJava(evalJS("12500"), 0));
    assertEquals(12500, JS.toJava(evalJS("12500"), (long) 0));
    assertEquals((float) 12500, JS.toJava(evalJS("12500.00"), (float) 0), 0.00001);
    assertEquals((double) 12500, JS.toJava(evalJS("12500.00"), (double) 0), 0.00001);
    assertEquals(12500, JS.toJava(evalJS("12500.00"), 0));
    assertEquals(1.25, JS.toJava(evalJS("1.25"), (float) 0), 0.00001);
    assertEquals(1.25, JS.toJava(evalJS("1.25"), (double) 0), 0.00001);

    // Not all floating point can be represented precisely.
    Number num = (Number) evalJS("123.4");
    assertEquals(123, num.intValue());
    assertEquals(123.4, num.floatValue(), 0.00001); // 123.4000015258789>
    assertEquals(123.4, num.doubleValue(), 0.00001);
    assertEquals("123.4", num.toString());

    // An integer inside an object, e.g. x.b, is stored as a java Double.
    js.checkTypes("({b:1,a:'2'}).a", "String", "java.lang.String");
    js.checkTypes("({b:1,a:'2'}).b", "Number", "java.lang.Double");
    // An integer inside an array, e.g. x[0], is stored as a java Double.
    js.checkTypes("([1,'a',3.5])[0]", "Number", "java.lang.Double");
    js.checkTypes("([1,'a',3.5])[1]", "String", "java.lang.String");
    js.checkTypes("([1,'a',3.5])[2]", "Number", "java.lang.Double");

    // Strings are optimized to java.lang.String in almost all cases.
    js.checkTypes("'abc'", "String", "java.lang.String");
    js.checkTypes("'ab'+3", "String", "java.lang.String");
    js.checkTypes("'ab'+'c'", "String", "java.lang.String");
    assertEquals("abc", JS.toJavaString(js.eval("'abc'"), ""));
    assertEquals("ab3", JS.toJavaString(js.eval("'ab' + 3"), ""));
    assertEquals("abc", JS.toJavaString(js.eval("'ab' + 'c'"), ""));
    js.checkTypes(
        "({a:'z',b:'x'+'y'}).b", "String", "java.lang.String"); // constant 'x'+'y' optimized?
    evalJS("x={a:'s'};x.a+='2';x.b='s2';");
    js.checkTypes("x.a", "String", "java.lang.String");
    js.checkTypes("x.b", "String", "java.lang.String");

    // toSource() output readable form of arrays and objects.
    js.checkTypes("[1,'a',3.5]", "Array", "org.mozilla.javascript.NativeArray");
    js.check("([1,'a',3.5]).toSource()", "[1, \"a\", 3.5]");
    js.checkTypes("({b:1,a:'2'})", "Object", "org.mozilla.javascript.NativeObject");
    evalJS("({b:1,a:'2'}).toSource()", "({b:1, a:\"2\"})");
  }

  @Test
  public void nativeObjectString() {
    // A JS object is a Rhino NativeObject, which is a Java Map.
    String code = "x={c:1,a:'s',b:[1,2]};x.a+='t'+'r';x.a2='st2';x;";
    // Note that Rhino 1.7R4 and beyond uses ConsString for x.a, but String for x.a2.
    @SuppressWarnings("unchecked") // will test obj in assertions
    Map<String, Object> obj = (Map<String, Object>) evalJS(code);
    String names = "a,c,a2,b"; // for a set, order does not matter
    Set<String> keys = new HashSet<>();
    Collections.addAll(keys, names.split(","));
    assertEquals(keys, obj.keySet());
    assertEquals(1, ((Number) obj.get("c")).intValue());
    assertEquals("org.mozilla.javascript.ConsString", obj.get("a").getClass().getName());
    assertEquals("java.lang.String", obj.get("a2").getClass().getName());
    // NOTE! obj.get("a") is a ConsString, while x.a is a String.
    js.checkTypes("x.a", "String", "java.lang.String");
    js.checkTypes("x.a2", "String", "java.lang.String");
    assertEquals("str", JS.toJavaString(obj.get("a"), ""));
    assertEquals("st2", JS.toJavaString(obj.get("a2"), ""));
    assertEquals("str", obj.get("a").toString());
    assertEquals("st2", obj.get("a2").toString());
  }

  @Test
  public void nativeObjectArray() {
    String code = "x={b:[1,2]}";
    @SuppressWarnings("unchecked") // will test obj in assertions
    Map<String, Object> obj = (Map<String, Object>) evalJS(code);
    // A JS Array is a Rhino NativeArray, which is a Java List of Number,
    // Each Number can be Integer or Double.
    // assertEquals does not compare list of mixed number types.
    // So, here we convert all Numbers to Double and compare.
    @SuppressWarnings("unchecked") // will test result in assertions
    List<Number> result = (List<Number>) obj.get("b");
    assertThat(result.get(0)).isInstanceOf(Double.class); // b[0] = 1.0
    assertThat(result.get(1)).isInstanceOf(Integer.class); // b[1] = 2
    List<Number> doubleNumbers = new ArrayList<>();
    for (Number n : result) {
      doubleNumbers.add(n.doubleValue());
    }
    List<Number> expected = Arrays.asList(new Double[] {1.0, 2.0});
    assertEquals(expected, doubleNumbers);
  }

  @Test
  public void addNativeObject() {
    // Convenient but slower to define and init a global JS variable.
    @SuppressWarnings("unchecked")
    Scriptable obj = (Scriptable) evalJS("CL={abc:123};");
    assertNotNull(obj);
    assertEquals("getClass", "org.mozilla.javascript.NativeObject", obj.getClass().getName());
    assertEquals("getClassName", "Object", obj.getClassName());
    js.check("CL.abc", 123);
    js.check("CL.xyz", "undefined");

    // The easy way to add a variable to the global scope.
    Scriptable globalScope = js.getScope(); // the global scope
    Scriptable cl2 = js.newObject();
    globalScope.put("CL2", globalScope, cl2); // define and init a new global variable
    cl2.put("x", cl2, 246);
    js.check("CL2.x", 246);
    Object cl2x = evalJS("CL2.x");
    assertEquals("java.lang.Integer", cl2x.getClass().getName());
    globalScope.put("CL2", globalScope, 135); // change its value
    js.check("CL2", 135);
    Object newCL2 = evalJS("CL2");
    assertEquals("java.lang.Integer", newCL2.getClass().getName());

    // Portable but slower to change a global JS variable.
    obj = (Scriptable) evalJS("CL={};");
    assertEquals("org.mozilla.javascript.NativeObject", obj.getClass().getName());
    js.check("CL.abc", "undefined");
    // obj.put("xyz", "x7"); // put is defined to match java.util.Map, but not implemented
    // java.lang.UnsupportedOperationException
    //     at org.mozilla.javascript.NativeObject.put(NativeObject.java:598)
    obj.put("abc", obj, 456);
    obj.put("xyz", obj, "x7");
    js.check("CL.xyz", "x7");
    js.check("CL.abc", 456);
    obj.put("abc", obj, 4.75);
    js.check("CL.abc", 4.75, 0.0); // 4.75 can be represented exactly, but not 4.56
    evalJS("CL.a1=[3,45];");
    js.check("CL.a1", "3,45");
    assertEquals("org.mozilla.javascript.NativeArray", evalJS("CL.a1").getClass().getName());
    Object obj2 = evalJS("CL");
    assertEquals(obj, obj2);
    obj.put("a1", obj, evalJS("CL.a2=[1,3,5];CL.a2"));
    js.check("CL.a1", "1,3,5");

    // The easy way to create a Rhino NativeArray.
    Object[] a1 = {3, "x5", 0.25};
    obj.put("a1", obj, js.newArray(a1));
    js.check("CL.a1", "3,x5,0.25");

    // The easy way to create a Rhino NativeObject.
    Scriptable obj3 = js.newObject();
    obj3.put("x", obj3, 777);
    obj.put("a3", obj, obj3);
    js.check("CL.a3.x", 777);
  }

  @Test
  public void addCLObject() {
    Scriptable cl = js.newObject();
    Scriptable scope = js.getScope();
    scope.put("CL", scope, cl);
    cl.put("abc", cl, 456);
    js.check("CL", "[object Object]");
    js.check("CL.abc", 456);
    js.check("CL.xyz", "undefined");
    js.checkTypes("CL.abc", "Number", "java.lang.Integer");
    js.checkTypes("CL", "Object", "org.mozilla.javascript.NativeObject");
  }

  @Test
  public void callJava() {
    // Calls to Java static functions are easy, although slow.
    // Note that JS number 100 is passed as double 100.0 to Java.
    js.check("x=10;java.lang.String.valueOf(x*10);", "100.0");
    js.check("(x*10).toString()", "100");
  }

  @Test
  public void syntaxError() {
    // What exceptions are thrown for syntax/undefined errors?
    String error = js.eval2String("x x @ y"); // syntax error
    assertEquals(JS.rhinoSyntaxError("missing ; before statement", "<str>", 1), error);
    error = js.eval2String("a.b.c"); // undefined
    assertEquals(JS.rhinoUndefinedError("a", "<str>", 1), error);
  }

  @Test
  public void newScope() {
    JS js1 = new JS("a=1");
    assertEquals("1", js1.eval2String("a"));
    js1.eval("a=2; b=3");
    assertEquals("2", js1.eval2String("a"));
    assertEquals("3", js1.eval2String("b"));
    JS js2 = new JS(js1); // get js1 init code, not current status
    assertEquals("2", js1.eval2String("a"));
    assertEquals("1", js2.eval2String("a"));
    js2.eval("a=4; c=5");
    assertEquals("2", js1.eval2String("a"));
    assertEquals("4", js2.eval2String("a"));
    assertEquals("5", js2.eval2String("c"));
    assertEquals(JS.rhinoUndefinedError("c", "<str>", 1), js1.eval2String("c"));
  }
}
