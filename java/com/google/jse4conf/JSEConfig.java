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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RefSpec;
import org.mozilla.javascript.NativeObject;

/**
 * JSEConfig extends jgit.lib.Config and accepts JS expressions as key values.
 *
 * <p>There are getJS* methods to get the JS expression values, similar to the Config.get* methods
 * to get the non-JS key values. However, there is no setJS* methods, because JS values are
 * constants after compileJS.
 */
public class JSEConfig extends Config {

  private final Config baseConfig;

  private String jsCode; // initial JS code to be executed before compileJS
  private Object compiledJSObject; // saved result Object of compileJS

  /** map from a (combined) section name to the Section object */
  private Map<String, Section> sections;

  /** map from a (combined) section name to the JS NativeObject, maps of key to JS values */
  private Map<String, Map<String, Object>> sectionValues;

  public JSEConfig() {
    baseConfig = null;
    reset(null);
  }

  public JSEConfig(Config defaultConfig) {
    this(defaultConfig, null);
  }

  public JSEConfig(Config defaultConfig, String jsCode) {
    super(defaultConfig);
    // Since the base class is not really set up like the given defaultConfig could have been,
    // some method like getNames need to be delegated manually.
    baseConfig = defaultConfig;
    reset(jsCode);
    compileJS();
  }

  private void reset(String jsCode) {
    sections = new HashMap<>();
    sectionValues = new HashMap<>();
    setJSCode(jsCode);
  }

  public JSEConfig setJSCode(String jsCode) {
    this.jsCode = jsCode;
    compiledJSObject = null;
    return this;
  }

  public Object getCompiledJSObject() {
    return compiledJSObject;
  }

  @Override
  public Set<String> getNames(String section, String subsection) {
    return (baseConfig == null)
        ? super.getNames(section, subsection)
        : baseConfig.getNames(section, subsection);
  }

  public Set<String> getJSNames(String section) {
    Section s = sections.get(section);
    return (s == null) ? new HashSet<>() : s.getKeys();
  }

  public Set<String> getJSNames(String section, String subsection) {
    return getJSNames(Section.nameOf(section, subsection));
  }

  private Section compile2Section(JS js, String sectionName, String subsectionName) {
    Section section = new Section(sectionName, subsectionName);
    Set<String> keys = getNames(sectionName, subsectionName);
    for (String k : keys) {
      section.addKeyValue(k, getString(sectionName, subsectionName, k));
    }
    section.compile(js);
    return section;
  }

  /** Compile all (sub)sections that have useJSE=true. */
  public boolean compileJS() {
    return compileJS(false);
  }

  /** Compile all (sub)sections with either useJSE=true or compileAll=true. */
  public boolean compileJS(boolean compileAll) {
    boolean success = true;
    for (String s : getSections()) {
      if (compileAll || getBoolean(s, "useJSE", false)) {
        success = compileJS(s) && success;
      }
      for (String sub : getSubsections(s)) {
        if (compileAll || getBoolean(s, sub, "useJSE", false)) {
          success = compileJS(s, sub) && success;
        }
      }
    }
    return success;
  }

  /** Compile the specified section. */
  public boolean compileJS(String section) {
    return compileJS(section, null);
  }

  /** Compile the specified (sub)section; subsection could be null. */
  public boolean compileJS(String section, String subsection) {
    String sectionName = Section.nameOf(section, subsection);
    JS js = new JS(jsCode); // new JS environment for each (sub)section
    Section sectionObject = compile2Section(js, section, subsection);
    sections.put(sectionName, sectionObject);
    sectionValues.remove(sectionName);
    String code = sectionObject.dumpJSCode(true);
    try {
      compiledJSObject = js.eval(code, sectionName);
      if (compiledJSObject instanceof NativeObject) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) compiledJSObject;
        sectionValues.put(sectionName, new HashMap<>(map));
      } else {
        return false; // caller can check error in compiledJSObject
      }
    } finally {
      js.exit();
    }
    return true;
  }

  /** Return true if a section has a key with JavaScript value. */
  public boolean hasJSKey(String section, String name) {
    Section s = sections.get(section);
    return (s == null) ? false : s.hasParsedKey(name);
  }

  /** Return true if a subsection has a key with JavaScript value. */
  public boolean hasJSKey(String section, String subsection, String name) {
    return hasJSKey(Section.nameOf(section, subsection), name);
  }

  /** Return the JavaScript value of section.name, or null. */
  public Object getJSValue(String section, String name) {
    Map<String, Object> s = sectionValues.get(section);
    return (s == null) ? null : s.get(name);
  }

  /** Return the JavaScript value of the name in a subsection, or null. */
  public Object getJSValue(String section, String subsection, String name) {
    return getJSValue(Section.nameOf(section, subsection), name);
  }

  /** Return JavaScript value of section.name as int, or the default value. */
  public int getJSInt(String section, String name, int defaultValue) {
    return getJSInt(section, null, name, defaultValue);
  }

  /** Return JavaScript value of section.subsection.name as int, or the default value. */
  public int getJSInt(String section, String subsection, String name, int defaultValue) {
    return hasJSKey(section, subsection, name)
        ? JS.toJava(getJSValue(section, subsection, name), defaultValue)
        : getInt(section, subsection, name, defaultValue);
  }

  /** Return JavaScript value of section.name as long, or the default value. */
  public long getJSLong(String section, String name, long defaultValue) {
    return getJSLong(section, null, name, defaultValue);
  }

  /** Return JavaScript value of section.subsection.name as long, or the default value. */
  public long getJSLong(String section, String subsection, String name, long defaultValue) {
    return hasJSKey(section, subsection, name)
        ? JS.toJava(getJSValue(section, subsection, name), defaultValue)
        : getLong(section, subsection, name, defaultValue);
  }

  /** Return JavaScript value of section.name as boolean, or the default value. */
  public boolean getJSBoolean(String section, String name, boolean defaultValue) {
    return getJSBoolean(section, null, name, defaultValue);
  }

  /** Return JavaScript value of section.subsection.name as boolean, or the default value. */
  public boolean getJSBoolean(
      String section, String subsection, String name, boolean defaultValue) {
    return hasJSKey(section, subsection, name)
        ? JS.toJava(getJSValue(section, subsection, name), defaultValue)
        : getBoolean(section, subsection, name, defaultValue);
  }

  /** Return JavaScript value of section.name as String, or null. */
  public String getJSString(String section, String name) {
    return getJSString(section, null, name);
  }

  /** Return JavaScript value of section.subsection.name as String, or null. */
  public String getJSString(String section, String subsection, String name) {
    return hasJSKey(section, subsection, name)
        ? getJSValue(section, subsection, name).toString()
        : getString(section, subsection, name);
  }

  private Config makeTempConfig(String section, String subsection, String name) {
    Config cfg = this;
    if (hasJSKey(section, subsection, name)) {
      cfg = new Config();
      String value = getJSValue(section, subsection, name).toString();
      cfg.setString(section, subsection, name, value);
    }
    return cfg;
  }

  /** Call Config.getEnum to convert JS String. */
  public <T extends Enum<?>> T getJSEnum(
      String section, String subsection, String name, T defaultValue) {
    Config cfg = makeTempConfig(section, subsection, name);
    return cfg.getEnum(section, subsection, name, defaultValue);
  }

  /** Call Config.getEnum to convert JS String. */
  public <T extends Enum<?>> T getJSEnum(
      T[] all, String section, String subsection, String name, T defaultValue) {
    Config cfg = makeTempConfig(section, subsection, name);
    return cfg.getEnum(all, section, subsection, name, defaultValue);
  }

  /** Call Config.getTimeUnit to convert JS String; return long like getTimeUnit. */
  public long getJSTimeUnit(
      String section, String subsection, String name, long defaultValue, TimeUnit wantUnit) {
    Config cfg = makeTempConfig(section, subsection, name);
    return cfg.getTimeUnit(section, subsection, name, defaultValue, wantUnit);
  }

  /** Call Config.getRefSpecs to convert JS String. */
  public List<RefSpec> getJSRefSpecs(String section, String subsection, String name) {
    Config cfg = makeTempConfig(section, subsection, name);
    return cfg.getRefSpecs(section, subsection, name);
  }

  private static String notFoundSection(String name) {
    return "// Section " + name + " not found\n";
  }

  /** Returns all JavaScript key-value pairs of a section, with JavaScript parsed source code. */
  public String dumpJSSource(String section) {
    return dumpJSSource(section, null);
  }

  /** Returns all JavaScript key-value pairs of a subsection, with JavaScript parsed source code. */
  public String dumpJSSource(String section, String subsection) {
    String sectionName = Section.nameOf(section, subsection);
    Section sect = sections.get(sectionName);
    return sect == null ? notFoundSection(sectionName) : sect.dumpJSCode();
  }

  /** Returns all JavaScript key-value pairs of a section, with evaluated JavaScript values. */
  public String dumpJSValues(String section) {
    return dumpJSValues(section, null);
  }

  /** Returns all JavaScript key-value pairs of a subsection, with evaluated JavaScript values. */
  public String dumpJSValues(String section, String subsection) {
    String sectionName = Section.nameOf(section, subsection);
    Section sect = sections.get(sectionName);
    return sect == null ? notFoundSection(sectionName) : sect.dumpJSKeyValues(jsCode);
  }
}
