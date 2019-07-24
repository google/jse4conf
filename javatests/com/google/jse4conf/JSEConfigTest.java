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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JSEConfigTest extends ConfTestBase {

  public enum TestSize {
    SMALL,
    LARGE,
    UNKNOWN
  };

  private static void checkSanityGetS1Default(String tag, Config config) {
    assertEquals(tag, "1+1", config.getString("s1", null, "k1"));
    assertTrue(tag, config.getBoolean("s1", "k2", false));
    assertEquals(tag, "'t'+k1", config.getString("s1", null, "v1"));
    // Config.getInt throws exception:
    //   java.lang.IllegalArgumentException: Invalid integer value: s1.v1='t'+k1
    // assertEquals(tag, "7", config.getInt("s1", "v1", 7));
    assertEquals(tag, "true", config.getString("s1", null, "k2"));
    assertNull(tag, config.getString("s1", null, "k3"));
  }

  private static void checkSanityGetS2Default(String tag, Config config) {
    assertEquals(tag, "'abc'", config.getString("s1", "s2", "k3"));
  }

  private static void checkSanityGetDefault(String tag, Config config, int n1, int n2) {
    assertThat(config.getNames("s1")).hasSize(n1);
    assertThat(config.getNames("s1", "s2")).hasSize(n2);
    checkSanityGetS1Default(tag, config);
    checkSanityGetS2Default(tag, config);
  }

  private static void checkGetJSS1Default(JSEConfig myConfig) {
    assertEquals("string", "1+1", myConfig.getJSString("s1", "k1"));
    assertTrue("boolean", myConfig.getJSBoolean("s1", "k2", false));
    assertEquals("string", "true", myConfig.getJSString("s1", "k2"));
    assertEquals("string", "'t'+k1", myConfig.getJSString("s1", "v1"));
    assertNull(myConfig.getJSString("s1", "k3"));
  }

  private static void checkGetJSS2Default(JSEConfig myConfig) {
    assertEquals("string", "'abc'", myConfig.getJSString("s1", "s2", "k3"));
  }

  private static void checkGetJSDefault(JSEConfig myConfig) {
    checkGetJSS1Default(myConfig);
    checkGetJSS2Default(myConfig);
  }

  private static void checkGetJSS1(JSEConfig myConfig) {
    assertEquals("string", "2", myConfig.getJSString("s1", "k1"));
    assertEquals("int", 2, myConfig.getJSInt("s1", "k1", 5));
    assertEquals("int", 8, myConfig.getJSInt("s1", "k3", 8));
    assertTrue("boolean", myConfig.getJSBoolean("s1", "k2", false));
    assertEquals("string", "true", myConfig.getJSString("s1", "k2"));
    assertEquals("string", "t2", myConfig.getJSString("s1", "v1"));
    assertNull(myConfig.getJSString("s1", "k3"));
  }

  private static void checkGetJSS2(JSEConfig myConfig) {
    assertEquals("string", "abc", myConfig.getJSString("s1", "s2", "k3"));
  }

  private static void checkGetJS(JSEConfig myConfig) {
    checkGetJSS1(myConfig);
    checkGetJSS2(myConfig);
  }

  private static JSEConfig newJSEConfig(String content) throws Exception {
    Config config = new Config();
    config.fromText(content);
    JSEConfig myConfig = new JSEConfig(config);
    assertTrue(myConfig.compileJS(true));
    return myConfig;
  }

  @Test
  public void sanity() throws Exception {
    String content = "[s1]\nk1=1+1\nk2=true\nv1='t'+k1\n[s1 \"s2\"]\nk3='abc'\n";
    Config config = new Config();
    config.fromText(content);
    checkSanityGetDefault("Config", config, 3, 1);

    // Create and test a JSEConfig as a wrapper of a Config.
    JSEConfig myConfig = new JSEConfig(config);
    checkSanityGetDefault("JSEConfig1", myConfig, 3, 1);

    // Before comileJS, getJS* return the same value as get*.
    checkGetJSDefault(myConfig);

    // Default compileJS only changes sections with useJSE=true.
    // So, the key values are still like default strings.
    assertTrue(myConfig.compileJS());
    checkGetJSDefault(myConfig);

    // Call compileJS s1.s2 and only s1.s2 should have JS value.
    assertTrue(myConfig.compileJS("s1", "s2"));
    checkGetJSS1Default(myConfig);
    checkGetJSS2(myConfig);

    // Reset and call compileJS(true) to compile all sections and subsections.
    myConfig = new JSEConfig(config);
    assertTrue(myConfig.compileJS(true));
    checkGetJS(myConfig);

    // After compileJS, get* can still return original strings.
    checkSanityGetDefault("JSEConfig2", myConfig, 3, 1);

    // Call compileJS s1 and only s1 should have JS value.
    myConfig = new JSEConfig(config);
    assertTrue(myConfig.compileJS("s1"));
    checkGetJSS1(myConfig);
    checkGetJSS2Default(myConfig);

    // Now check useJSE only in s1, default compileJS only compiles s1.
    String content1 = "[s1]\nk1=1+1\nk2=true\nv1='t'+k1\nuseJSE=true\n[s1 \"s2\"]\nk3='abc'\n";
    config = new Config();
    config.fromText(content1);
    myConfig = new JSEConfig(config);
    assertTrue(myConfig.compileJS());
    checkGetJSS1(myConfig);
    checkGetJSS2Default(myConfig);
    checkSanityGetDefault("JSEConfig3", myConfig, 4, 1);

    // Now check useJSE only in s1, default compileJS only compiles s2.
    String content2 = "[s1]\nk1=1+1\nk2=true\nv1='t'+k1\n[s1 \"s2\"]\nuseJSE=true\nk3='abc'\n";
    config = new Config();
    config.fromText(content2);
    myConfig = new JSEConfig(config);
    assertTrue(myConfig.compileJS());
    checkGetJSS2(myConfig);
    checkGetJSS1Default(myConfig);
    checkSanityGetDefault("JSEConfig4", myConfig, 3, 2);
  }

  private static String illegalArgument(String msg) {
    return "java.lang.IllegalArgumentException: " + msg;
  }

  public static String invalidValue(String expr) {
    return illegalArgument("Invalid value: " + expr);
  }

  @Test
  public void testGetEnum() throws Exception {
    String content = "[s1]\nt1=SMALL\nt2=LARGE\nt3='LAR'+'GE'\nuseJSE=true\n";
    JSEConfig cfg = newJSEConfig(content);
    assertEquals(TestSize.UNKNOWN, cfg.getEnum("s1", null, "t0", TestSize.UNKNOWN));
    assertEquals(TestSize.SMALL, cfg.getEnum("s1", null, "t1", TestSize.UNKNOWN));
    assertEquals(TestSize.LARGE, cfg.getEnum("s1", null, "t2", TestSize.UNKNOWN));
    try {
      cfg.getEnum("s1", null, "t3", TestSize.UNKNOWN);
      fail("s1.t3 should have thrown exception.");
    } catch (IllegalArgumentException e) {
      assertEquals(invalidValue("s1.t3='LAR'+'GE'"), e.toString());
    }
    assertEquals(TestSize.UNKNOWN, cfg.getJSEnum("s1", null, "t0", TestSize.UNKNOWN));
    assertEquals(TestSize.SMALL, cfg.getJSEnum("s1", null, "t1", TestSize.UNKNOWN));
    assertEquals(TestSize.LARGE, cfg.getJSEnum("s1", null, "t2", TestSize.UNKNOWN));
    assertEquals("LARGE", cfg.getJSString("s1", null, "t3"));
    assertEquals(TestSize.LARGE, cfg.getJSEnum("s1", null, "t3", TestSize.UNKNOWN));
    // Given array of enum values.
    TestSize[] sizes = {TestSize.LARGE, TestSize.SMALL};
    assertEquals(TestSize.UNKNOWN, cfg.getJSEnum(sizes, "s1", null, "t0", TestSize.UNKNOWN));
    assertEquals(TestSize.SMALL, cfg.getJSEnum(sizes, "s1", null, "t1", TestSize.UNKNOWN));
    assertEquals(TestSize.LARGE, cfg.getJSEnum(sizes, "s1", null, "t2", TestSize.UNKNOWN));
    assertEquals(TestSize.LARGE, cfg.getJSEnum(sizes, "s1", null, "t3", TestSize.UNKNOWN));
    TestSize[] oneSize = {TestSize.UNKNOWN, TestSize.SMALL};
    assertEquals(TestSize.UNKNOWN, cfg.getJSEnum(oneSize, "s1", null, "t0", TestSize.UNKNOWN));
    assertEquals(TestSize.SMALL, cfg.getJSEnum(oneSize, "s1", null, "t1", TestSize.UNKNOWN));
    try {
      cfg.getJSEnum(oneSize, "s1", null, "t2", TestSize.UNKNOWN);
      fail("s1.t2 should have thrown exception.");
    } catch (IllegalArgumentException e) {
      assertEquals(invalidValue("s1.t2=LARGE"), e.toString());
    }
    try {
      cfg.getJSEnum(oneSize, "s1", null, "t3", TestSize.UNKNOWN);
      fail("s1.t3 should have thrown exception.");
    } catch (IllegalArgumentException e) {
      assertEquals(invalidValue("s1.t3=LARGE"), e.toString());
    }
  }

  @Test
  public void testGetTimeUnit() throws Exception {
    String content = "[T1]\ns1=3\ns2=(s1+1)+'ms'\ns3=s1+'sec'\ns4=123ms\n";
    JSEConfig cfg = newJSEConfig(content);
    assertEquals(4, cfg.getJSTimeUnit("T1", null, "s2", -1, TimeUnit.MILLISECONDS));
    assertEquals(3000, cfg.getJSTimeUnit("T1", null, "s3", -1, TimeUnit.MILLISECONDS));
    assertEquals(3, cfg.getJSTimeUnit("T1", null, "s3", -1, TimeUnit.SECONDS));
    assertEquals(123, cfg.getTimeUnit("T1", null, "s4", -1, TimeUnit.MILLISECONDS));
    // TODO: fix JS parsing and not to insert ";"
    // s4 is converted to "123; ms" by Rhino.
    try {
      cfg.getJSTimeUnit("T1", null, "s4", -1, TimeUnit.MILLISECONDS);
      fail("T1.s4 should have thrown exception.");
    } catch (IllegalArgumentException e) {
      assertEquals(illegalArgument("Invalid time unit value: T1.s4=123; ms"), e.toString());
    }
  }

  @Test
  public void testGetRefSpecs() throws Exception {
    // jgit RefSpec.java includes the following "specifications".
    String[] refs = {
      "refs/heads/master",
      "heads/master:refs/remotes/origin/master",
      "heads/*:refs/remotes/origin/*",
      "+heads/master",
      "+heads/master:refs/remotes/origin/master",
      "+heads/*:refs/remotes/origin/*",
      "+pull/*/head:refs/remotes/origin/pr/*",
      ":heads/master",
    };
    for (String r : refs) {
      String content = "[R1]\nr1=" + r + "\nr2='" + r + "'\nr1=" + r + "\n";
      JSEConfig cfg = newJSEConfig(content);
      List<RefSpec> specs = cfg.getRefSpecs("R1", null, "r1");
      assertThat(specs).hasSize(2);
      assertEquals(r, specs.get(0).toString());
      assertEquals(r, specs.get(1).toString());
      // getJSRefSpecs returns only one pattern,
      // because each JS variable can have only one value.
      specs = cfg.getJSRefSpecs("R1", null, "r2");
      assertThat(specs).hasSize(1);
      assertEquals(r, specs.get(0).toString());
    }
  }

  @Test
  public void testDumpJSSource() throws Exception {
    String content = "[T1]\nuseJSE=true\nk1=k2*2\nk2=1+1\n[T2 \"s1\"]\nuseJSE=true\nk3=CL.branch\n";
    String initJS = "CL={branch:'beta'}";
    Config config = new Config();
    config.fromText(content);
    JSEConfig myConfig = new JSEConfig(config, initJS);
    assertTrue(myConfig.compileJS());
    String t1Dump =
        "var T1 = function() {\n  const k2 = 1 + 1;\n  const k1 = k2 * 2;\n"
            + "  const useJSE = true;\n  return {k1:k1,k2:k2,useJSE:useJSE,};\n}();\n";
    String t2Dump =
        "var T2S1 = function() {\n  const k3 = CL.branch;\n  const useJSE = true;\n"
            + "  return {k3:k3,useJSE:useJSE,};\n}();\n";
    assertEquals(t1Dump, myConfig.dumpJSSource("T1", null));
    assertEquals(t2Dump, myConfig.dumpJSSource("T2", "s1"));
  }

  @Test
  public void testDumpJSValues() throws Exception {
    String content = "[T1]\nuseJSE=true\nk1=CL.branch\nk2=CL.proj + ':' + CL.branch\n";
    String initJS = "CL={branch:'beta', proj:'P1/P2'}";
    Config config = new Config();
    config.fromText(content);
    JSEConfig myConfig = new JSEConfig(config, initJS);
    assertTrue(myConfig.compileJS());
    String t1SourceDump =
        "/*\nvar T1 = function() {\n  const k1 = CL.branch;\n"
            + "  const k2 = CL.proj + ':' + CL.branch;\n  const useJSE = true;\n"
            + "  return {k1:k1,k2:k2,useJSE:useJSE,};\n}();\n*/\n";
    String t1Dump =
        t1SourceDump
            + "var T1 = {\n  k1: \"beta\",\n"
            + "  k2: \"P1/P2:beta\",\n  useJSE: true,\n};\n";
    assertEquals(t1Dump, myConfig.dumpJSValues("T1", null));
    // Same JSEConfig now compiled and evaluated with different initJS code.
    initJS = "CL={branch:'test', proj:'P2'}";
    assertTrue(myConfig.setJSCode(initJS).compileJS());
    String t2Dump =
        t1SourceDump
            + "var T1 = {\n  k1: \"test\",\n"
            + "  k2: \"P2:test\",\n  useJSE: true,\n};\n";
    assertEquals(t2Dump, myConfig.dumpJSValues("T1", null));
  }
}
