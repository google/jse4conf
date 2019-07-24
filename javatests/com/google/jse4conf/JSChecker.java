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

/** Utility functions to check JS eval results. */
public class JSChecker extends JS {

  public JSChecker() {}

  public JSChecker(JS js) {
    super(js);
  }

  public void check(String tag, String jsCode, String expected) {
    assertEquals(tag, expected, eval2String(jsCode));
  }

  public void check(String jsCode, String expected) {
    check(jsCode, jsCode, expected);
  }

  public void check(String jsCode, boolean expected) {
    Object obj = eval(jsCode);
    assertThat(obj).isInstanceOf(Boolean.class);
    assertEquals(expected, ((Boolean) obj).booleanValue());
  }

  public void check(String jsCode, int expected) {
    Object obj = eval(jsCode);
    assertThat(obj).isInstanceOf(Number.class);
    assertEquals(expected, ((Number) obj).intValue());
  }

  public void check(String jsCode, double expected, double delta) {
    Object obj = eval(jsCode);
    assertThat(obj).isInstanceOf(Number.class);
    assertEquals(expected, ((Number) obj).doubleValue(), delta);
  }

  public void checkJSON(String code, String expected) {
    check("JSON.stringify(" + code + ")", expected);
  }

  public void checkJavaType(String jsCode, String javaClassName) {
    assertEquals(javaClassName, eval(jsCode).getClass().getName());
  }

  public void checkJSType(String jsCode, String jsTypeName) {
    assertEquals(jsTypeName, eval2String("(" + jsCode + ").constructor.name"));
  }

  public void checkTypes(String jsCode, String jsType, String javaClass) {
    checkJSType(jsCode, jsType);
    checkJavaType(jsCode, javaClass);
  }
}
