// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.jse4conf;

import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

/** Rhino JS/Java conversions, naming, and error messages. */
public class JS {
  private Context context; // perthread Rhino runtime context
  private Scriptable scope; // Rhino JS global scope
  private String initCode; // Initialization after Rhino startup code

  /** Create a new global default scope. */
  public JS() {
    this((String) null);
  }

  /** Create a clone with same initCode. */
  public JS(JS js) {
    this(js.initCode);
  }

  /** Create a new global default scope initilized with given JS code. */
  public JS(String initCode) {
    context = null;
    reset(initCode);
  }

  /** Reset to a new global scope. */
  public void reset() {
    reset(null);
  }

  /** Reset to a new global scope with optional initCode and return last value or null. */
  public Object reset(String initCode) {
    exit();
    context = ContextFactory.getGlobal().enterContext();
    scope = context.initStandardObjects();
    this.initCode = initCode;
    // caller should check if the returned object is an Exception.
    return (initCode == null) ? null : eval(initCode, "<init>");
  }

  /** Must call exit before release this object. */
  public void exit() {
    if (context != null) {
      Context.exit();
      context = null;
      scope = null;
      initCode = null;
    }
  }

  /** Return this context. */
  public Context getContext() {
    return context;
  }

  /** Return this global scope. */
  public Scriptable getScope() {
    return scope;
  }

  /** Create JS NativeArray in this context and global scope. */
  public NativeArray newArray(int length) {
    Scriptable obj = context.newArray(scope, length);
    return (obj instanceof NativeArray) ? ((NativeArray) obj) : null;
  }

  /** Create JS NativeArray in this context and global scope. */
  public NativeArray newArray(Object[] elements) {
    Scriptable obj = context.newArray(scope, elements);
    return (obj instanceof NativeArray) ? ((NativeArray) obj) : null;
  }

  /** Create JS NativeObject in this context and global scope. */
  public Scriptable newObject() {
    return context.newObject(scope);
  }

  /** Eval JS code in this context and scope, caller should catch exception. */
  public Object eval(String code, String fileName) {
    return eval(context, scope, code, fileName);
  }

  public Object eval(String code) {
    return eval(code, "<str>");
  }

  /** Eval JS code of a file in the given context and scope. */
  public static Object eval(Context cx, Scriptable scope, String code, String file) {
    try {
      return cx.evaluateString(scope, code, file, 1, null);
    } catch (Exception e) {
      return e; // for negative tests to check errors
    }
  }

  /** Eval JS code in the given context and scope. */
  public static Object eval(Context cx, Scriptable scope, String code) {
    return eval(cx, scope, code, "<str>");
  }

  /** Eval JS code in this context and scope, no exception. */
  public String eval2String(String code, String fileName) {
    return eval2String(context, scope, code, fileName);
  }

  public String eval2String(String code) {
    return eval2String(code, "<str>");
  }

  /** Eval JS code in the given context and scope, and convert to a String. */
  public static String eval2String(Context cx, Scriptable scope, String code, String file) {
    try {
      return Context.toString(eval(cx, scope, code, file));
    } catch (Exception e) {
      return e.toString(); // for negative tests to check errors
    }
  }

  /** Eval JS code in the given context and scope, and convert to a String. */
  public static String eval2String(Context cx, Scriptable scope, String code) {
    return eval2String(cx, scope, code, "<str>");
  }

  // JS and Java type conversions:

  /** Convert JS obj to Java int, or return defaultInt. */
  public static int toJava(Object obj, int defaultInt) {
    return (obj instanceof Number) ? ((Number) obj).intValue() : defaultInt;
  }

  /** Convert JS obj to Java long, or return defaultLong. */
  public static long toJava(Object obj, long defaultLong) {
    return (obj instanceof Number) ? ((Number) obj).longValue() : defaultLong;
  }

  /** Convert JS obj to Java float, or return defaultFloat. */
  public static float toJava(Object obj, float defaultFloat) {
    return (obj instanceof Number) ? ((Number) obj).floatValue() : defaultFloat;
  }

  /** Convert JS obj to Java double, or return defaultDouble. */
  public static double toJava(Object obj, double defaultDouble) {
    return (obj instanceof Number) ? ((Number) obj).doubleValue() : defaultDouble;
  }

  /** Convert JS obj to Java boolean, or return defaultBoolean. */
  public static boolean toJava(Object obj, boolean defaultBoolean) {
    return (obj instanceof Boolean) ? ((Boolean) obj).booleanValue() : defaultBoolean;
  }

  /** Convert JS obj to Java String, or return defaultString. */
  public static String toJavaString(Object obj, String defaultString) {
    try {
      return Context.jsToJava(obj, String.class).toString();
    } catch (EvaluatorException e) {
      return defaultString;
    }
  }

  /** Convert Java int to JS Object. */
  public static Integer from(int value) {
    return value; // use Java default conversion
  }

  /** Convert Java long to JS Object. */
  public static Long from(long value) {
    return value; // use Java default conversion
  }

  /** Convert Java float to JS Object. */
  public static Float from(float value) {
    return value;
  }

  /** Convert Java int to JS Object. */
  public static Double from(double value) {
    return value;
  }

  /** Convert Java boolean to JS Object. */
  public static Boolean from(boolean value) {
    return value;
  }

  // Dump JS objects/values to text.

  /** Dump the key value of an object, of the given name, in the simplest JS source form. */
  public String dumpObjectKeyValue(String name, NativeObject obj, String key) {
    Object v = obj.get(key, obj);
    if (v instanceof Integer || v instanceof Boolean) {
      return v.toString();
    }
    if (v instanceof Number) { // Print as integer if possible.
      long n = ((Number) v).longValue();
      double d = ((Number) v).doubleValue();
      return (d == n) ? String.valueOf(n) : v.toString();
    }
    // Print as JSON quoted string, or the JS source code.
    String expr =
        (v instanceof ConsString || v instanceof String)
            ? ("JSON.stringify(" + name + "." + key + ")")
            : (name + "." + key + ".toSource()");
    return eval(expr, expr).toString();
  }

  /** Dump all elements of a named object, with indentation. */
  private String dumpObjectElements(String indent, String name, NativeObject obj) {
    String result = "";
    for (Object key : obj.getIds()) {
      result += indent + key + ": " + dumpObjectKeyValue(name, obj, key.toString()) + ",\n";
    }
    return result;
  }

  /** Return the JS source form of a named variable; enclosed in comment if inComment is true. */
  public String dumpSource(String name, boolean inComment) {
    Object obj = scope.get(name, scope);
    if (obj instanceof NativeObject) {
      return ((inComment ? "/*\nvar " : "var ") + name + " = {\n")
          + dumpObjectElements("  ", name, (NativeObject) obj)
          + ("};\n" + (inComment ? "*/\n" : ""));
    } else {
      return ((inComment ? "// var " : "var ") + name + " = ")
          + (eval(name + ".toSource()", name) + ";\n");
    }
  }

  // Rhino dependent run time exceptions:

  /** Exception thrown by Rhino for a syntax error. */
  public static String rhinoSyntaxError(String msg, String file, int lineNum) {
    return String.format(
        "org.mozilla.javascript.EvaluatorException: %s (%s#%d)", msg, file, lineNum);
  }

  /** Exception thrown by Rhino for an undefined name. */
  public static String rhinoUndefinedError(String name, String file, int lineNum) {
    return String.format(
        "org.mozilla.javascript.EcmaError: ReferenceError: \"%s\" is not defined. (%s#%d)",
        name, file, lineNum);
  }

  // jse4conf specific error messages:

  /** Error message for a key that has no parsed value. */
  public static String keyMissingValue(String key) {
    return "  // ERROR: no value for key: " + key + "\n";
  }

  /** Error message for a key's JavaScript expression with given exception. */
  public static String keyValueException(String key, String value, String e) {
    return "  // ERROR: " + key + " = " + value + "\n  // " + e + "\n";
  }

  /** Error message for undefined names in a key's JavaScript expression. */
  public static String undefinedError(String key, String value, String name) {
    return keyValueException(key, value, rhinoUndefinedError(name, key, 1));
  }

  // jse4conf specific naming convention:

  /** Check if c is a valid character for a jse4conf identifier. */
  public static boolean validIdChar(char c) {
    return Character.isLetter(c) || Character.isDigit(c);
  }

  /** Convert a string to a valid jse4conf identifier. */
  public static String toJSName(String name) {
    if (name == null || name.length() < 2) {
      return name; // name is at minimal length
    }
    StringBuilder result = new StringBuilder(name.length());
    boolean wasInvalidIdChar = false;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (validIdChar(c)) {
        // Make the first letter after invalid chars upper case.
        // So, "aa.bb-.cc" becomes "aaBbCc".
        result.append(wasInvalidIdChar ? Character.toUpperCase(c) : c);
        wasInvalidIdChar = false;
      } else {
        wasInvalidIdChar = true;
      }
    }
    return result.toString();
  }
}
