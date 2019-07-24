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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CycleTest extends ConfTestBase {
  // Test input with cyclic used names.

  @Test
  public void callSelf() {
    // A key can call itself in a function body.
    // No cycle should be reported.
    String input =
        "[testFact]\n f1 = (n) => \\\n  n<2 \\\n ? 1\\\n : n + f1(n-1)\n"
            + " f2 = (n) => n < 2 ? 1 : n * f2(n - 1)\n";
    String output =
        "var testFact = function() {\n"
            + "  const f1 = (n) => n < 2 ? 1 : n + f1(n - 1);\n"
            + "  const f2 = (n) => n < 2 ? 1 : n * f2(n - 1);\n"
            + "  return {f1:f1,f2:f2,};\n}();\n";
    assertEquals(output, compileConf(input));
    assertEquals(output, compileConf(input, true));
    assertEquals(output, compileConf(input));
  }

  @Test
  public void acyclicDependency() {
    // A longer test of acyclic dependencies.
    String input =
        "[t3]\na=1\nb=a+c# use on a and c\nc=100\n"
            + "z = (n) => (n < 2) ? 1 : n*z(n-1)\nk1='hello'+k3\n"
            + "k2 = 10000 + z(4)\nk3 = ' world'\n";
    String output =
        "var t3 = function() {\n  const a = 1;\n  const c = 100;\n"
            + "  const b = a + c;\n  const k3 = ' world';\n  const k1 = 'hello' + k3;\n"
            + "  const z = (n) => (n < 2) ? 1 : n * z(n - 1);\n  const k2 = 10000 + z(4);\n"
            + "  return {a:a,b:b,c:c,k1:k1,k2:k2,k3:k3,z:z,};\n}();\n";
    assertEquals(output, compileConf(input));
    assertEquals(output, compileConf(input, true));
    assertEquals(output, compileConf(input));
  }

  // In cyclic reference tests, dependency lists can be used to
  // break the cycles and make output keys in desired order.
  // But references to not-yet-defined names are still errors.
  // Those error values could be converted to default strings,
  // if there is a JS environment to test/eval the key values.
  @Test
  public void twoCycles() {
    // Two big cycles detected, without user input dependency list.
    String input =
        "[twoCycles]\nz3=3+z7\nz4=4+z5+z9\nz5=5+z6\nz6=6+z7\nz7=7+z8\n"
            + "z8=8+z5\nz9=9+z1\nz1=1+z4\n";
    String output =
        "var twoCycles = function() {\n  const z8 = 8 + z5;\n  const z7 = 7 + z8;\n"
            + "  const z6 = 6 + z7;\n  const z5 = 5 + z6;\n  const z9 = 9 + z1;\n"
            + "  const z4 = 4 + z5 + z9;\n  const z1 = 1 + z4;\n  const z3 = 3 + z7;\n"
            + "  return {z1:z1,z3:z3,z4:z4,z5:z5,z6:z6,z7:z7,z8:z8,z9:z9,};\n"
            + "  // cycle: z5 => z6 => z7 => z8 => z5\n"
            + "  // cycle: z1 => z4 => z9 => z1\n"
            + "  // cycle: z7 => z8 => z5 => z6 => z7\n"
            + "  // cycle: z4 => z9 => z1 => z4\n"
            + "  // cycle: z6 => z7 => z8 => z5 => z6\n"
            + "  // cycle: z8 => z5 => z6 => z7 => z8\n"
            + "  // cycle: z9 => z1 => z4 => z9\n}();\n";
    assertEquals(output, compileConf(input));
    assertEquals(output, compileConf(input)); // Repeated compilation is okay.
    // When compileConf without a JS environment, there is no test eval of key values,
    // and thus cyclic references like "z6 = 6 + z7" are not converted to strings.
  }

  @Test
  public void dependencyList1() {
    // Two cycles detected, one resolved by dependency list zz.
    String input =
        "[twoCycles]\nz3=3+z7\nz4=4+z5+z9\nz5=5+z6\nz6=6+z7\nz7=7+z8\n"
            + "z8=8+z5\nz9=9+z1\nz1=1+z4\nzz=[z5,z7]\n";
    String output =
        "var twoCycles = function() {\n  const z6 = '6 + z7';\n  const z5 = 5 + z6;\n"
            + "  const z8 = 8 + z5;\n  const z7 = 7 + z8;\n  const z9 = '9 + z1';\n"
            + "  const z4 = 4 + z5 + z9;\n  const z1 = 1 + z4;\n  const z3 = 3 + z7;\n"
            + "  const zz = [z5, z7];\n"
            + JS.undefinedError("z6", "6 + z7", "z7")
            + JS.undefinedError("z9", "9 + z1", "z1")
            + "  return {z1:z1,z3:z3,z4:z4,z5:z5,z6:z6,z7:z7,z8:z8,z9:z9,zz:zz,};\n"
            + "  // cycle: z1 => z4 => z9 => z1\n"
            + "  // cycle: z4 => z9 => z1 => z4\n"
            + "  // cycle: z9 => z1 => z4 => z9\n}();\n";
    assertEquals(output, compileConf(input, true));
    assertEquals(output, compileConf(input, true));
    // When compileConf with JS environment, test eval of key values will convert cyclic references
    // like "z6 = 6 + z7" to strings "z6 = '6 + z7'".
  }

  @Test
  public void dependencyList2() {
    // Two cycles detected, both resolved by dependency list zz.
    String input =
        "[twoCycles]\nz3=3+z7\nz4=4+z5+z9\nz5=5+z6\nz6=6+z7\nz7=7+z8\n"
            + "z8=8+z5\nz9=9+z1\nz1=1+z4\nzz=[z5,z7,z1,z9]\n";
    String output =
        "var twoCycles = function() {\n  const z6 = '6 + z7';\n  const z5 = 5 + z6;\n"
            + "  const z8 = 8 + z5;\n  const z7 = 7 + z8;\n  const z4 = '4 + z5 + z9';\n"
            + "  const z1 = 1 + z4;\n  const z9 = 9 + z1;\n  const z3 = 3 + z7;\n"
            + "  const zz = [z5, z7, z1, z9];\n"
            + JS.undefinedError("z6", "6 + z7", "z7")
            + JS.undefinedError("z4", "4 + z5 + z9", "z9")
            + "  return {z1:z1,z3:z3,z4:z4,z5:z5,z6:z6,z7:z7,z8:z8,z9:z9,zz:zz,};\n}();\n";
    assertEquals(output, compileConf(input, true));
    assertEquals(output, compileConf(input, true)); // Repeated compilation is okay.
  }
}
