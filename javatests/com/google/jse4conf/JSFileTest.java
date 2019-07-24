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
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JSFileTest extends ConfTestBase {

  static final String DATA_DIR = System.getProperty("data.dir");

  // Load a file and check final result string.
  private void loadFileTest(String tag, String fileName, String result) throws Exception {
    // System.out.println("#### Working Directory = " + System.getProperty("user.dir"));
    // System.out.println("####    DATA_DIR = " + DATA_DIR);
    Object obj = resetJSFromFile(DATA_DIR + fileName);
    assertEquals(tag, result, obj.toString());
  }

  // Load a config file and compile it into JS code.
  private String conf2js(String initCode, String fileName) throws Exception {
    try {
      String text = readFile(DATA_DIR + fileName);
      assertNotNull(text);
      Conf2JS conf2JS = new Conf2JS(js, initCode);
      // compileConfString by default converts all sections.
      String result = conf2JS.compileConfString(text);
      // Dump the compiled result to help debug failed tests.
      System.out.println("### Dump conf2js result of: " + fileName);
      System.out.println(result);
      return result;
    } catch (Exception e) {
      System.out.println("Error in conf2js: " + e);
      return null;
    }
  }

  @Test
  public void testDumpers() throws Exception {
    resetJSFromFile(DATA_DIR + "dumpers.js.in");
    String output = "# x:\na=3\nf=(n) => 4 + n\nk=v\n";
    js.check("x={k:'v',a:3,f:(n)=>4+n,}; DumpObject('x');", output);
    js.check("DumpEval('3+5')", "# (3+5)=8\n");
    js.check("eval('3+5')", 8);
  }

  @Test
  public void conf2JSDumpers() throws Exception {
    // Test from jse4conf intput to parsed JS and DumpObject + DumpEval.
    String input = "[t1]\nf2=(n) => n<2 ? 1 : n * f2(n-1)\n[t2]\nf2=(n) => 1 + t1.f2(n)\n";
    String output =
        "var t1 = function() {\n  const f2 = (n) => n < 2 ? 1 : n * f2(n - 1);\n"
            + "  return {f2:f2,};\n}();\n"
            + "var t2 = function() {\n  const f2 = (n) => 1 + t1.f2(n);\n"
            + "  return {f2:f2,};\n}();\n";

    assertEquals("compile t1", output, compileConf(input));
    resetJSFromFile(DATA_DIR + "dumpers.js.in");
    evalJS(output);
    js.check("DumpObject('t1')", "# t1:\nf2=(n) => n < 2 ? 1 : n * f2(n - 1)\n");
    js.check("DumpObject('t2')", "# t2:\nf2=(n) => 1 + t1.f2(n)\n");
    js.check("DumpEval('t1.f2(5)')", "# (t1.f2(5))=120\n");
    js.check("DumpEval('t2.f2(5)')", "# (t2.f2(5))=121\n");
    js.check("t1.f2(5)", 120);
    js.check("t2.f2(5)", 121);
  }

  @Test
  public void loadTest1() throws Exception {
    // Load one JS file and check the last expression value.
    loadFileTest("test1", "test1.js.in", "{\"k3\":[1,2]}");
  }

  @Test
  public void gerrit() throws Exception {
    // Test mocked predefined variables in gerrit.js.in.
    resetJSFromFile(DATA_DIR + "gerrit.js.in");
    js.check("CL.Author.Email", "u101@g.com");
    js.check("CL.Branch", "refs/beta");
    js.check("CL.Committer.Id", 102);
    js.check("CL.Files.length + 10", 12);
    js.check("CL.IsPureRevert", false);
    js.check("CL.Labels.length", 1);
    js.check("CL.Labels[0].Name", "Code-Review");
    js.check("CL.Labels[0].Value", 1);
    js.check("CL.Labels[0].User", 101);
    js.check("CL.Message == 'A test CL'", true);
    js.check("CL.NumFiles.Inserted", 0);
    js.check("CL.NumFiles.Modified", 2);
    js.check("CL.NumUnresolvedComments", 0);
    js.check("CL.Project", "ProjectA");
    js.check("CL.Uploader.Id + 100", 142);
    js.check("CL.Uploader.Name", "Smith");
    js.checkJSType("CL.commitDelta", "Function");
    js.check("CL.commitDelta(/test.[a-n]/)", false);
    js.check("CL.commitDelta(/test.[m-z]/)", true);
    js.check("CL.commitDelta(/OWN/)", true);
    js.check("CL.commitDelta(new RegExp('OWN.*S'))", true);
    js.check("CL.commitDelta(new RegExp('^t'))", true);
    js.check("CL.commitDelta(new RegExp('^e'))", false);
    js.check("CL.removeLabel('Code-Review', CL.Labels).length", 0);
    js.check("CL.removeLabel('code-review', CL.Labels).length", 1);
    js.check("FindOwners.submitFilter(CL.Labels) == CL.Labels", true);
  }

  @Test
  public void projectConfig() throws Exception {
    // Test a config file with JSE with the following steps:

    // (1) Load mocked predefined Gerrit and CL variables from a JS file,
    //     and check the mocked predefined values.
    String initCode = Conf2JS.readAllBytes(DATA_DIR + "gerrit.js.in");
    resetJS(initCode);
    js.check("CL.Uploader.Id", 42);
    js.check("CL.Project", "ProjectA");

    // (2) Translate the config file to a JavaScript string,
    //     and check for embedded error messages in returned jsInput.
    String jsInput = conf2js(initCode, "project.config");
    assertNotNull(jsInput);
    // Check for expected error, but no other error.
    String expectedError = JS.undefinedError("ownersFileName", "OWNERS.android", "OWNERS");
    assertThat(jsInput, containsString(expectedError));
    assertThat(jsInput.replace(expectedError, "")).doesNotContain("ERROR");

    // (3) Evaluate the translated JavaScript code and then check JS key values.
    evalJS(jsInput);
    js.check("pluginFindOwners.enable", true);
    js.check("pluginFindOwners.ownersFileName", "OWNERS.android");
    js.check("pluginFindOwners.maxCacheAge", 30);
    js.check("pluginFindOwners.useJSE", true);
    js.check("pluginFindOwners.removeOwnerApproved", true);
    js.check("pluginFindOwners.BCOLabel", "Build-Cop-Override");
    js.check("pluginFindOwners.ExemptUsers", "104,106");
    js.check("CL.Uploader.Id", 42);

    // We can also check multiple key values in one call to js.check
    // A JS array of [a,b,c,d] to converted to string "a,b,c,d"
    String conditions =
        "[pluginFindOwners.isExemptUploader,"
            + "pluginFindOwners.hasBuildCopOverride,"
            + "pluginFindOwners.isExemptFromReviews,"
            + "pluginFindOwners.optOutFindOwners,"
            + "pluginFindOwners.optInFindOwners,"
            + "pluginFindOwners.needOwnerReview]";
    js.check("6 conditions", conditions, "false,false,false,false,true,true");

    // (4) We can change some CL variables and reload the config file.
    //     For example, change Branch name and recheck new key values.
    evalJS("CL.Branch = 'refs/heads/my-experiment';\n" + jsInput);
    js.check("opt-out-branch", conditions, "false,false,false,true,true,false");
    // Another example to change multiple CL variables, and reload config file.
    evalJS("CL.Uploader.Id = 106;CL.Branch = 'master';");
    js.check("CL.Uploader.Id", 106);
    js.check("CL.Branch", "master");
    evalJS(jsInput);
    js.check("isExemptUploader", conditions, "true,false,true,false,true,false");

    // Finally, test the other demo section in the config file!
    js.check("[pluginDemo.fac4,pluginDemo.fac5]", "24,120");
    js.check("pluginDemo.fac(3)", 6);
    // Conf2JS convert a section's key-value pairs to a JS NativeObject which is a Java Map.
    @SuppressWarnings("unchecked") // will test demo's elements in assertions
    Map<String, Object> demo = (Map<String, Object>) evalJS("pluginDemo");
    Set<String> keys = new HashSet<>();
    keys.add("fac");
    keys.add("fac4");
    keys.add("fac5");
    keys.add("useJSE");
    assertEquals(keys, demo.keySet());
    assertEquals(24, ((Number) demo.get("fac4")).intValue());
    assertEquals(120, ((Number) demo.get("fac5")).intValue());
  }
}
