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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/** Converts a JSE config file to JavaScript code. */
public class Conf2JS {
  private final Logger logger;
  private final JS js; // Rhino context+scope to test eval parsed values
  private final String initCode; // predefined code for JS environment
  private boolean dumpJSValues; // with initCode, dump its value and JS section values

  // section names or section-subsection names returned by config parser
  private List<String> sectionList;

  private Map<String, Section> sections; // name in sectionList => Section.

  private String compilationErrors; // collect compilation errors.

  Conf2JS() {
    this(false, false, null, null);
  }

  Conf2JS(JS js) {
    this(false, false, js, null);
  }

  Conf2JS(JS js, String initCode) {
    this(false, false, js, initCode);
  }

  Conf2JS(boolean debug, boolean trace) {
    this(debug, trace, null, null);
  }

  Conf2JS(boolean debug, boolean trace, JS js, String initCode) {
    logger = new Logger(debug, trace);
    this.js = js;
    this.initCode = initCode;
    this.dumpJSValues = false;
  }

  public Conf2JS setDumpJSValues(boolean newValue) {
    this.dumpJSValues = (newValue && js != null && initCode != null);
    return this;
  }

  public String getCompilationErrors() {
    return compilationErrors;
  }

  private String dumpSectionKeyCode() {
    String code = "";
    for (String name : sectionList) {
      Section section = sections.get(name);
      code += dumpJSValues ? section.dumpJSKeyValues(initCode) : section.dumpJSCode();
    }
    if (compilationErrors != null && compilationErrors.length() > 0) {
      code += compilationErrors;
    }
    return code;
  }

  private void compileSection(Config cfg, String section, String subsection, boolean compileAll) {
    // Skip a (sub)section if useJSE is not true and not compileAll.
    String sectionName = Section.nameOf(section, subsection);
    if (!compileAll && !cfg.getBoolean(section, subsection, "useJSE", false)) {
      logger.debug("### skip non-JSE Section: " + sectionName);
      return;
    }
    Set<String> keys = cfg.getNames(section, subsection);
    if (keys.size() < 1) {
      logger.debug("### skip empty Section: " + sectionName);
      return;
    }
    logger.debug("### compile Section: " + sectionName);
    sectionList.add(sectionName);
    Section sectionObject = new Section(sectionName, logger);
    sections.put(sectionName, sectionObject);
    for (String k : keys) {
      sectionObject.addKeyValue(k, cfg.getString(section, subsection, k));
    }
    sectionObject.compile(js);
    if (js != null) {
      js.reset(initCode); // reset to compile the next section
    }
  }

  /** Compile sections in content to JS code, if useJSE is true in a (sub)section. */
  public String compileConfString(String content) {
    return compileConfString(content, false);
  }

  /** Compile sections in content to JS code, if useJSE is true in a (sub)section or compileAll. */
  public String compileConfString(String content, boolean compileAll) {
    sectionList = new ArrayList<>();
    compilationErrors = "";
    Config cfg = new Config();
    try {
      // Need to replace "\r\n" with "\n" for DOS format files.
      content = content.replace("\r\n", "\n");
      cfg.fromText(content);
      sections = new HashMap<>();
      // Although getSections returns a Set<String>, it says that
      // the set's iterator returns sections in the order they are
      // declared by the configuration.
      for (String s : cfg.getSections()) {
        compileSection(cfg, s, null, compileAll);
        for (String sub : cfg.getSubsections(s)) {
          compileSection(cfg, s, sub, compileAll);
        }
      }
    } catch (ConfigInvalidException e) {
      String error = "ERROR: " + e.getMessage();
      logger.debug(error);
      compilationErrors += error;
    }
    return dumpSectionKeyCode();
  }

  public Set<String> getJSSectionNames() {
    Set<String> names = new HashSet<>();
    for (String name : sectionList) {
      names.add(JS.toJSName(name));
    }
    return names;
  }

  public void compileFile(String inF, String outF) throws IOException {
    logger.debug("To compile file " + inF + " to " + outF);
    String jsCode = "";
    if (dumpJSValues) {
      js.reset(initCode);
      jsCode =
          Arrays.stream(js.getScope().getIds())
              .map(id -> js.dumpSource(id.toString(), false))
              .reduce(jsCode, (x, y) -> x + y);
    }
    jsCode += compileConfString(readAllBytes(inF));
    writeToFile(outF, jsCode);
  }

  /** Read bytes from a file. */
  public static String readAllBytes(String filePath) throws IOException {
    return new String(Files.readAllBytes(Paths.get(filePath)), UTF_8);
  }

  /** Write a string to a file. */
  public static void writeToFile(String filePath, String content) throws IOException {
    try (Writer fileWriter = Files.newBufferedWriter(Paths.get(filePath), UTF_8)) {
      fileWriter.write(content);
    }
  }

  public static void main(String[] args) throws IOException {
    boolean debug = false;
    boolean trace = false;
    String jsFile = null;
    String inF = null;
    String outF = null;
    for (int i = 0; i < args.length; ++i) {
      String arg = args[i];
      if (arg.equals("-d")) {
        debug = true;
      } else if (arg.equals("-t")) {
        trace = true;
      } else if (arg.equals("-js")) {
        ++i;
        if (i >= args.length) {
          System.out.println("Need JS file after -js flag.");
          System.exit(1);
        }
        jsFile = args[i];
      } else if (inF == null) {
        inF = arg;
      } else if (outF == null) {
        outF = arg;
      } else {
        System.out.println("Extra argument: " + arg);
        System.exit(1);
      }
    }
    if (inF != null && outF != null) {
      String initCode = (jsFile == null) ? null : readAllBytes(jsFile);
      new Conf2JS(debug, trace, new JS(initCode), initCode)
          .setDumpJSValues(true)
          .compileFile(inF, outF);
    }
  }
}
