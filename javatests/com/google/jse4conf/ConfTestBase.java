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

import java.io.IOException;

/** Add Config file related test supports to TestBase. */
public class ConfTestBase extends TestBase {

  protected String compileConf(String input) {
    return compileConf(input, false);
  }

  protected String compileConf(String input, boolean useJS) {
    if (useJS) {
      js.reset(); // every compileConf test uses a new JS environment
    }
    return new Conf2JS(useJS ? js : null).compileConfString(input, true);
  }

  protected Object resetJSFromFile(String file) throws Exception {
    return resetJS(Conf2JS.readAllBytes(file));
  }

  protected String readFile(String file) throws IOException {
    return Conf2JS.readAllBytes(file);
  }

  protected Object loadFile(String file) throws IOException {
    return evalJS(readFile(file), file);
  }
}
