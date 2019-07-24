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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Conf2JSTest extends ConfTestBase {
  // NOTE: to skip a test, replace @Test with @Ignore.

  @Test
  public void comments() {
    // # comments are removed, "key=jse" => "const key=exp;" in a closure.
    String input = "[test1] #comment\nk1  =1  #one\n  k2=k1+1; ;  \n";
    String output =
        "var test1 = function() {\n  const k1 = 1;\n"
            + "  const k2 = k1 + 1;\n  return {k1:k1,k2:k2,};\n}();\n";
    assertEquals(output, compileConf(input));
  }

  @Test
  public void semicolons() {
    // Semicolons start line comments to EOL in JGit Config parser.
    // Use double quotes to include special characters.
    // * e1:  1;2;3;;  ==> 1
    // * e2:  {3;5}    ==> {3  =>  {3;}
    // * g1:  "{3;5}"  ==> {3;5} => {3;5;}
    String input = "[s1]\ne1=1;2;3;;\ne2={3;5}\ng1=\"{3;5}\"\n";
    String output =
        "var s1 = function() {\n  const e1 = 1;\n  const e2 = { 3; };\n"
            + "  const g1 = { 3; 5; };\n  return {e1:e1,e2:e2,g1:g1,};\n}();\n";
    assertEquals(output, compileConf(input));
  }

  @Test
  public void escapeChars() {
    // JGit Config parser accepts:  \"  \\  \n  \t  \b
    String input = "[s1]\ne1='\\\"a\\\\\\\"bx\\tcd\\be'\n";
    String output =
        "var s1 = function() {\n"
            + "  const e1 = '\"a\"bx\\tcd\\be';\n"
            + "  return {e1:e1,};\n}();\n";
    assertEquals(output, compileConf(input));
  }

  @Test
  public void escapeSemicolon() {
    // JGit Config parser does not recognize \;
    String input = "[s1]\ne1={3\\;5}\n";
    // Older JGit Config.java returns error message as:
    String output = "ERROR: Bad escape: ;";
    // Newer JGit Config.java returns error message as:
    // "ERROR: Bad escape: \\u003b";
    assertEquals(output, compileConf(input).replace("\\u003b", ";"));
    input = "[s2]\ne2=\"{3\\;5}\"\n";
    assertEquals(output, compileConf(input).replace("\\u003b", ";"));
  }

  @Test
  public void sectionOrder() {
    // Order of sections in .conf are preserved.
    String input = "[sEc-1]\nk1=1\n[sEC-2]\nk2=2\n";
    String sec1 = "var sEc1 = function() {\n  const k1 = 1;\n" + "  return {k1:k1,};\n}();\n";
    String sec2 = "var sEC2 = function() {\n  const k2 = 2;\n" + "  return {k2:k2,};\n}();\n";
    String output = sec1 + sec2;
    assertEquals(output, compileConf(input));
    input = "[sEC-2]\nk2=2\n[sEc-1]\nk1=1\n";
    output = sec2 + sec1;
    assertEquals(output, compileConf(input));
  }

  @Test
  public void referenceOtherKeys() {
    String input = "[t1]\nk1=k2.x\nk2=({d:1,x:2})\nk3=k4().y\nk4=()=>({a:1,y:3})\n";
    String output =
        "var t1 = function() {\n  const k2 = ({ d: 1, x: 2});\n"
            + "  const k1 = k2.x;\n  const k4 = () => ({ a: 1, y: 3});\n"
            + "  const k3 = k4().y;\n  return {k1:k1,k2:k2,k3:k3,k4:k4,};\n}();\n";
    String result = compileConf(input);
    assertEquals(output, result);
    assertThat(result).doesNotContain("ERROR");
  }

  @Test
  public void propertyGet() {
    // Collect used names in ast.PropertyGet and eval them in dependency order..
    String input = "[t1]\nk1=k2.x\nk2=({d:1,x:2})\nk3=k4(5).y\nk4=(n)=>({a:n,y:n*n})\n";
    evalJS(compileConf(input));
    js.check("t1.k1", 2);
    js.check("t1.k3", 25);
    js.check("t1.k2.d", 1);
    js.check("t1.k4(3).a", 3);
  }

  @Test
  public void subSection() {
    // Subsections are ordered as if sections, and named as <sectionSubsection>.
    // Empty sections are skipped.
    String input = "[sEc-1]\nk1=1\n[sEC-2 \"sUb--seC\"]\nk2=2\n";
    String sec1 = "var sEc1 = function() {\n  const k1 = 1;\n" + "  return {k1:k1,};\n}();\n";
    String sec2sub =
        "var sEC2SUbSeC = function() {\n  const k2 = 2;\n" + "  return {k2:k2,};\n}();\n";
    String output = sec1 + sec2sub;
    assertEquals(output, compileConf(input));
    input = "[sEC-2 \"sUb-..-seC\"]\nk2=2\n[sEc-1]\nk1=1\n";
    output = sec2sub + sec1;
    assertEquals(output, compileConf(input));
  }

  @Test
  public void defaultString() {
    // JSE with errors are treated as strings.
    String input = "[tsec]\nk1=k2+1\nk2=2\nk3=OWNERS\nk4=a.b.c\n";
    String output =
        "var tsec = function() {\n  const k2 = 2;\n"
            + "  const k1 = k2 + 1;\n  const k3 = 'OWNERS';\n  const k4 = 'a.b.c';\n"
            + JS.undefinedError("k3", "OWNERS", "OWNERS")
            + JS.undefinedError("k4", "a.b.c", "a")
            + "  return {k1:k1,k2:k2,k3:k3,k4:k4,};\n}();\n";
    assertEquals(output, compileConf(input, true));
  }

  @Test
  public void toJSName() {
    // Section and subsection names are converted to valid JavaScript
    // identifiers, skipping invalid characters, and with the Camel notation.
    String[][] pairs = {
      {null, null},
      {"", ""},
      {"3", "3"},
      {"_", "_"},
      {"a", "a"},
      {"123", "123"},
      {"456", "_456"},
      {"Abc", "_abc"},
      {"aaBbCc", "aa.bb..-cc"},
      {"AaBbDd", "_aa_bb_.-dd"},
      {"123aaBbCc", "123aa_bb_.-cc"},
      {"s1D1", "s1.d1"},
      {"secAbc", "sec.abc"},
      {"secXyZ", "sec--.--.xyZ"},
      {"pluginFindOwners", "plugin-find-owners"}
    };
    for (String[] pair : pairs) {
      assertEquals(pair[0], JS.toJSName(pair[1]));
    }
  }

  @Test
  public void renameSections() {
    String input = "[s1.d1]\nk1=1\n[s2--d2]\nk2=2\n[s3.-.d3]\nk3=3\n[s4]\nk4=4\n";
    String output =
        "var s1D1 = function() {\n  const k1 = 1;\n  return {k1:k1,};\n}();\n"
            + "var s2D2 = function() {\n  const k2 = 2;\n  return {k2:k2,};\n}();\n"
            + "var s3D3 = function() {\n  const k3 = 3;\n  return {k3:k3,};\n}();\n"
            + "var s4 = function() {\n  const k4 = 4;\n  return {k4:k4,};\n}();\n";
    assertEquals(output, compileConf(input));
  }
}
