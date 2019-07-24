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

import java.time.Duration;
import java.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

public class TestBase {
  private Instant before; // Time before test.
  private Instant after; // Time after test.
  protected JSChecker js; // JS runtime test environment

  @Rule public TestName name = new TestName();

  @Before
  public void start() {
    before = Instant.now();
    js = new JSChecker();
  }

  @After
  public void end() {
    js.exit();
    after = Instant.now();
    System.out.printf(
        "#    test %s finished in %d msec.\n",
        name.getMethodName(), Duration.between(before, after).toMillis());
  }

  protected Object resetJS(String initCode) throws Exception {
    Object result = js.reset(initCode);
    if (result instanceof Exception) {
      throw (Exception) result;
    }
    return result;
  }

  // Return JS value object or Java Exception object.
  protected Object evalJS(String s, String file) {
    return js.eval(s, file);
  }

  protected Object evalJS(String s) {
    return evalJS(s, "<str>");
  }
}
