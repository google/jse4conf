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

import java.util.Set;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.Scope;

/** A logger object prints debug/trace messages depending on the flags. */
public class Logger {
  private boolean debug = false; // set to dump debug messages
  private boolean trace = false; // set to dump more than debug messages

  Logger(boolean debug, boolean trace) {
    this.debug = debug;
    this.trace = trace;
  }

  public static boolean println(String msg) {
    System.out.print(msg.endsWith("\n") ? msg : (msg + "\n"));
    return true;
  }

  public static String dumpKey(String kind, String key) {
    return dumpKey("## checking", kind, key);
  }

  public static String dumpKey(String prefix, String action, String key) {
    return "#" + prefix + " " + action + " key: " + key;
  }

  public boolean debug(String msg) {
    return debug && println(msg);
  }

  public boolean trace(String msg) {
    return trace && println(msg);
  }

  public boolean debugMissingValue(String key) {
    return debug && println("# ERROR: missing parsed value of " + key + " default to ''.");
  }

  public boolean debugKeyValue(String key, String value) {
    return debug && println("# ERROR: " + key + " = " + value);
  }

  public boolean debugKeyParsedValue(String key, String value) {
    return debug && println("### Test parse key:" + key + " value:" + value);
  }

  public boolean debugUsedKeys(String key, Set<String> used) {
    return debug && println("### Found names used by " + key + ": " + used);
  }

  public boolean debugKey(String kind, String key) {
    return debug && println(dumpKey(kind, key));
  }

  public boolean debugKey(String prefix, String action, String key) {
    return debug && println(dumpKey(prefix, action, key));
  }

  public boolean traceKey(String kind, String key) {
    return trace && println(dumpKey(kind, key));
  }

  public boolean traceKey(String prefix, String action, String key) {
    return trace && println(dumpKey(prefix, action, key));
  }

  public boolean traceAST(String value, AstRoot root) {
    return trace
        && println(
            "### parsed AST for [" + value + "](" + value.length() + "):\n" + root.debugPrint());
  }

  public boolean traceVisitNode(String type, AstNode node) {
    return trace && println("    visit " + type + ": " + node.toSource(0));
  }

  private static String scopeOf(Name node) {
    Scope scope = node.getDefiningScope();
    return scope == null ? "(null)" : scope.toSource(0);
  }

  public boolean traceVisitNameNode(Name node) {
    return trace && println("    visit Name: " + node.toSource(0) + " in scope: " + scopeOf(node));
  }
}
