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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

// All information in a config file section.
public class Section {
  // Just to parse list of ids, skip space and comma.
  static final String ID_REGEX = "[^\\]\\s,]+";
  static final String SPACE = "\\s*";
  static final Pattern ID_PAT = Pattern.compile(",?\\s*(" + ID_REGEX + ")");
  static final Pattern LIST_OF_ID_PAT =
      Pattern.compile("\\[(" + ID_REGEX + ")(," + SPACE + ID_REGEX + ")*\\]");

  private final String jsName; // section name converted for JavaScript
  private final Map<String, String> keyValues; // original section key -> value
  private Set<String> jsKeys; // JS.toJSName(k) of k in keyValues.keySet().
  private List<String> sortedKeys; // sorted jsKeys

  // from Rhino parser AST, set in findUsedNames:
  private Map<String, String> parsedValues; // parsed AST dump
  private Map<String, Set<String>> keyUseSet; // key -> used names
  private Set<String> visited; // set when searching for used names

  // Valid JS key-value pairs can have only dependency cycles:
  // (1) Direct recursive calls to a key in its own function body.
  // (2) At least 2 keys in every cycle are specified in a "dependency list".
  // A dependency list is a JavaScript list containing only key names
  // and assigned to a key. For example, the following dependency list:
  //     keyOrder = [key2, key5, key1]
  // tells Section.compile to emit key2, key5, and key1 in order, to break
  // any cyclic dependencies among those 3 keys. Note that other keys
  // that used by key2 will still be emitted before key2.
  private List<List<String>> cycles; // detected by findCycles

  private List<String> dependencyList; // empty or user provided.
  private Set<String> dependencySet; // keys in dependencyList

  // Keys are emitted in an order determined by the following rules:
  // (1) K1 before K2, if K1 is before K2 in the dependencyList, otherwise,
  // (2) K1 before K2, if keyUseSet.get(K2).contains(K1), otherwise,
  // (3) K1 before K2, if K1 is in dependency list, but K2 is not, otherwise,
  // (4) K1 before K2, if K1 is alphabetically before K2.
  private List<String> jsKeyList; // key names in JS-code order

  private List<String> errors; // parse errors

  private final Logger logger; // to dump debug/trace messages

  /** Create a combined name for the given section and subsection names. */
  public static String nameOf(String section, String subsection) {
    return (subsection == null) ? section : (section + "-" + subsection);
  }

  /** Create a Section of the given section and subsection names. */
  public Section(String section, String subsection) {
    this(nameOf(section, subsection), new Logger(false, false));
  }

  /** Create a Section of the given section name or combined section-subsection name. */
  public Section(String name) {
    this(name, new Logger(false, false));
  }

  /**
   * Create a Section of the given section name or combined section-subsection name, and output
   * debug/trace messages through the logger.
   */
  public Section(String name, Logger logger) {
    this.jsName = JS.toJSName(name);
    this.logger = logger;
    this.keyValues = new HashMap<>();
    // Other fields are initialized in compile().
  }

  /** Add a pair of original config file's key and value. */
  public void addKeyValue(String key, String value) {
    this.keyValues.put(key, value);
  }

  /** Return all keys defined in this section. */
  public Set<String> getKeys() {
    return keyValues.keySet();
  }

  /** Return all JS keys defined in this section. */
  public Set<String> getJSKeys() {
    return jsKeys;
  }

  /** Return the JS variable name for this section. */
  public String getJSName() {
    return jsName;
  }

  /** Return true if key has parsed JavaScript value. */
  public boolean hasParsedKey(String key) {
    return parsedValues.get(key) != null;
  }

  // Check if given value string is a "dependency list".
  private void findDependencyList(String value) {
    // scan parsed value for the pattern of "[id{, id}]"
    if (value == null
        || value.length() < 3
        || value.charAt(0) != '['
        || value.charAt(value.length() - 1) != ']') {
      return; // too short to be a valid list
    }
    Matcher m = LIST_OF_ID_PAT.matcher(value);
    if (m.matches()) {
      List<String> list = new ArrayList<>();
      logger.trace("# Check potential dependency list: " + value);
      // Extract one key at a time, because the Matcher returns only one last
      // key in the group for (...)*.
      int start = 1;
      m = ID_PAT.matcher(value);
      while (start < value.length() && m.find(start)) {
        String id = m.group(1);
        if (!jsKeys.contains(id)) {
          return; // not a valid key
        } else {
          list.add(id);
        }
        start = m.end();
      }
      dependencyList = !list.isEmpty() ? list : dependencyList;
      logger.trace("## New dependencyList = " + dependencyList);
    }
  }

  private void findUsedNames() {
    parsedValues = new HashMap<>();
    keyUseSet = new HashMap<>();
    visited = new HashSet<>();
    dependencyList = new ArrayList<>();
    dependencySet = new HashSet<>();
    for (String k : sortedKeys) {
      String value = keyValues.get(k);
      // Parse a key's value like an expression.
      Parser parser = new Parser(CompilerEnvirons.ideEnvirons());
      AstRoot root = parser.parse(value, "valueOf(" + k + ")", 1);
      // Null is returned if parse failed, then k is not added into parsedValues.
      if (root != null) {
        logger.traceAST(value, root);
        String parsedValue = removeExtraLF(root.toSource());
        parsedValues.put(k, parsedValue);
        findDependencyList(parsedValue);
        logger.debugKeyParsedValue(k, parsedValue);

        NameVisitor visitor = new NameVisitor(k, logger);
        root.visit(visitor);
        // Ignore used names that are not keys of this section.
        Set<String> usedKeys = new HashSet<>(visitor.usedNames);
        usedKeys.retainAll(sortedKeys);
        if (!usedKeys.isEmpty()) {
          keyUseSet.put(k, usedKeys);
          logger.debugUsedKeys(k, usedKeys);
        }
      }
    }
  }

  // Add one detected cycle into the list of all cycles.
  private void addCycle(List<String> cycle) {
    // Sequential search is slow only if there are many cycles.
    // Correct input should not have any cycle.
    for (List<String> x : cycles) {
      if (x.equals(cycle)) {
        return;
      }
    }
    cycles.add(cycle);
  }

  private void findCycles(ArrayDeque<String> stack, Set<String> useSet) {
    if (useSet == null) {
      return;
    }
    List<String> useList = new ArrayList<>(useSet);
    Collections.sort(useList);
    for (String name : useList) {
      if (stack.contains(name)) {
        List<String> c = new ArrayList<>();
        int foundInDependencySet = 0;
        boolean inCycle = false;
        for (String k : stack) {
          if (!inCycle) {
            inCycle = k.equals(name);
          }
          if (inCycle) {
            c.add(k);
            foundInDependencySet += dependencySet.contains(k) ? 1 : 0;
          }
        }
        // Ignore this cycle if 2 or more keys in the cycle have their
        // ordering specified in a dependency list. In that case, trust
        // user's dependency list to break/ignore this cyclic reference
        // by emitting the keys in dependency list first.
        if (foundInDependencySet < 2) {
          addCycle(c);
        }
      } else {
        stack.add(name);
        findCycles(stack, keyUseSet.get(name));
        stack.remove(name);
      }
    }
  }

  // Detect and collect all cycles in the useSet of every key.
  private void findCycles() {
    cycles = new ArrayList<>();
    dependencySet = new HashSet<>(dependencyList);
    for (String k : sortedKeys) {
      ArrayDeque<String> stack = new ArrayDeque<>();
      stack.add(k);
      findCycles(stack, keyUseSet.get(k));
    }
  }

  private static String removeExtraLF(String str) {
    // toSource always has ';\n' at the end.
    // and sometimes, extra '\n' to break long lines.
    return str.replaceAll(" *\n *", " ").replaceAll(";? *$", "");
  }

  private boolean needToDumpUsedKey(String key) {
    // The given key is used by some other key.
    // The used key is dumped before the other key, only if
    // it has valid parsed value, not visited yet, and not in the dependencySet.
    // Keys in the dependencySet should be dumped by the order in the dependencySet.
    return hasParsedKey(key) && !visited.contains(key) && !dependencySet.contains(key);
  }

  private void compileKey(String key, String indent) {
    if (visited.contains(key)) {
      logger.traceKey(indent, "Skip", key);
      return;
    }
    logger.traceKey(indent, "Check", key);
    visited.add(key);
    String value = parsedValues.get(key);
    if (value == null) {
      errors.add(JS.keyMissingValue(key));
      return;
    }
    // Must sort usedKeys to dump them in deterministic order.
    if (keyUseSet.get(key) != null) { // need to dump used keys first
      List<String> sortedUsedKeys = new ArrayList<>(keyUseSet.get(key));
      Collections.sort(sortedUsedKeys);
      for (String k : sortedUsedKeys) {
        if (needToDumpUsedKey(k)) {
          logger.traceKey(indent, "Check used", k);
          compileKey(k, indent + "  ");
        }
      }
    }
    logger.debugKey(indent, "Dump", key);
    jsKeyList.add(key);
  }

  // When JS environment js is available, evaluate all parsed JS strings.
  // If a parsed string has evaluation error, add single-quote characters
  // around the string to make it a default valid JS string.
  // This makes string values in old config files easily accepted as
  // JavaScript strings of a JSEConfig file.
  private void makeDefaultStrings(JS js) {
    if (js == null) {
      return; // no way to test parsed JS code.
    }
    js = new JS(js); // use only initCode of js, do not change original state
    for (String k : jsKeyList) {
      String value = parsedValues.get(k);
      if (value == null) {
        logger.debugMissingValue(k);
        parsedValues.put(k, "''");
      } else {
        Object obj = js.eval("const " + k + "=" + value + ";", k); // use key name as fake file name
        if (obj instanceof Throwable) {
          // Maybe it is good to output an error message here,
          // but not if this could flood a server's error log files.
          logger.debugKeyValue(k, value);
          errors.add(JS.keyValueException(k, value, obj.toString()));
          parsedValues.put(k, "'" + value + "'");
        }
      }
    }
  }

  /** Compile value strings without any JS environment. */
  public void compile() {
    compile(null);
  }

  /** Compile value strings with the given JS environment. */
  public void compile(JS js) {
    jsKeys = new HashSet<>();
    Set<String> invalidKeys = new HashSet<>(); // not valid JS var name
    for (String k : keyValues.keySet()) {
      String jsKeyName = JS.toJSName(k);
      jsKeys.add(jsKeyName);
      if (!k.equals(jsKeyName)) {
        invalidKeys.add(k);
      }
    }
    for (String k : invalidKeys) {
      // Add a new key with the valid name and original k's value.
      keyValues.put(JS.toJSName(k), keyValues.get(k));
    }
    sortedKeys = new ArrayList<>(jsKeys);
    Collections.sort(sortedKeys);
    findUsedNames(); // set up keyUseSet
    findCycles(); // set up cycles
    jsKeyList = new ArrayList<>();
    errors = new ArrayList<>();
    for (String k : dependencyList) {
      logger.traceKey("dependency", k);
      compileKey(k, "");
    }
    for (String k : sortedKeys) {
      logger.traceKey("sorted", k);
      compileKey(k, "");
    }
    // Now all value strings are parsed as JavaScript expressions.
    makeDefaultStrings(js);
  }

  // Return lines to report all detected cyclic used-names.
  private String dumpCycles() {
    StringBuilder output = new StringBuilder();
    for (List<String> c : cycles) {
      output.append("  // cycle:");
      for (String k : c) {
        output.append(" ").append(k).append(" =>");
      }
      output.append(" ").append(c.get(0)).append("\n");
    }
    return output.toString();
  }

  /** Return true if there was any syntax error. */
  public boolean hasError() {
    return !errors.isEmpty();
  }

  /** Dump JavaScript code to define an object variable containing this section's key-value. */
  public String dumpJSCode() {
    return dumpJSCode(false);
  }

  /** Dump JavaScript code, with an object value for this section's key-value. */
  public String dumpJSCode(boolean needObjValue) {
    StringBuilder code = new StringBuilder();
    code.append("var ").append(jsName).append(" = function() {\n");
    for (String key : jsKeyList) {
      code.append("  const ").append(key).append(" = ").append(parsedValues.get(key)).append(";\n");
    }
    for (String e : errors) {
      code.append(e);
    }
    // Wrap "return {...};" statement to under 80 character lines.
    String line = "  return {";
    String space = "          ";
    for (String k : sortedKeys) {
      String tmp = k + ":" + k + ",";
      if (line.length() + tmp.length() >= 80) {
        code.append(line).append("\n");
        line = space + tmp;
      } else {
        line += tmp;
      }
    }
    code.append(line).append("};\n");
    if (cycles != null) {
      code.append(dumpCycles());
    }
    code.append("}();\n");
    if (needObjValue) {
      code.append(jsName).append(";\n");
    }
    return code.toString();
  }

  /** Dump compiled JS code and that JS code value with given initCode. */
  public String dumpJSKeyValues(String initCode) {
    String sectionCode = dumpJSCode();
    JS js = new JS(initCode);
    js.eval(sectionCode);
    return "/*\n" + sectionCode + "*/\n" + js.dumpSource(getJSName(), false);
  }
}
