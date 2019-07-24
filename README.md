# Jse4conf Documentation

Copyright 2019 Google LLC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

----

## Table of Contents
* [Introduction](#introduction)
 * [SampleCL Example](#samplecl-example)
* [Dependency on Rhino](#dependency-on-rhino)
* [Dependency on JGit Config](#dependency-on-jgit-config)
* [Sample Code](#sample-code)
 * [Naming Convention](#naming-convention)
 * [Forward References](#forward-references)
 * [Wrong Cyclic References](#wrong-cyclic-references)
 * [Explicit Key Orders](#explicit-key-orders)
* [Testing Jse4conf](#testing-jse4conf)

## Introduction

Jse4conf is a parser and interpreter library for JavaScript expressions
embedded in a [configuration file](https://en.wikipedia.org/wiki/Configuration_file).
A jse4conf application is usually an extension
to some existing config file format.
Current jse4conf has wrapper `JSEConfig` class that extends the
[JGit Config](https://github.com/eclipse/jgit/blob/master/org.eclipse.jgit/src/org/eclipse/jgit/lib/Config.java)
file format.

Although there are multiple configuration file formats,
all of them contain key-value pairs that might be
grouped into *sections* and *subsections*.
The embedded JS expressions are just key values
parsed as strings by a configuration file parser.

Jse4conf evaluates those JS expressions within each (sub)section scope.
The expression can refer to other keys in the same (sub)section
like JS variables. They can also use any predefined JS variables
that might be provided by the jse4conf user.
*Current version of jse4conf does not support references from one config
(sub)section to another.*

Why do we need to use JS expressions in a simple config file?<br>
The answer is to provide more dynamic configurable key values.

Traditional software config files with key-value pairs only accept *static*
values for each key. Users can change a key's value,
but the value is fixed for the whole execution-time of the software program.
In applications with configurations depending on run-time variables,
the configuration key values are difficult to define and reload.
With embedded JS expressions, they can be written and parsed once
in a config file, then evaluated at run-time multiple times,
to get different values depending on other run-time variables.

## SampleCL Example

For example, a code review system has many changes (CLs) from various authors.
Some authors might be in a *super users* group with certain privilege.
If some review rules depend on such privilege, they can depend on some key
values in a config file. Suppose a key is named `authorIsSuperUser`, which
could be `true` or `false` depending on the author of a CL.
How do we write a *static* rule/expression in a config file,
and let its value depend on the CL to be found at run-time?

With embedded JS code in a config file, we can write in config file like this:
``` shell
[SampleCL] # a section name
  useJSE = true # enable JS code in this section
  authorIsSuperUser = isSuperUser(CL.Author)
  uploaderIsSuperUser = isSuperUser(CL.Uploader)
  isSuperUser = (x) => SuperUsers.indexOf(x.Id) >= 0
  SuperUsers = [101, 107]
```

The above code is interpreted with different CL bindings at run-time.
For example, for a CL from John Smith, the `CL` variable could be:
``` javascript
var CL = {};
CL.user = (email, id, name) => ({Email: email, Id: id, Name: name});
CL.Author = CL.user('u101@g.com', 101, 'John Smith');
CL.Uploader = CL.user('u102@g.com', 102, 'John');
```

With that CL binding,
the SampleCL config key value expressions will evaluate to:
``` javascript
var SampleCL = {
  SuperUsers: [101, 107],
  authorIsSuperUser: true,
  isSuperUser: (x) => SuperUsers.indexOf(x.Id) >= 0,
  uploaderIsSuperUser: false,
  useJSE: true,
};
```

With another CL binding, the same `SampleCL` section
could find a different value for `CL.Author` and
depending on that value, `SampleCL.authorIsSuperUser`
could be `true` or `false`.

In additional to traditional config file key value types,
an embedded JS expression can have any JS value type.
In our example, `SampleCL.SuperUsers` is a list of
numbers and `SampleCL.isSuperUser` is a JS function.


## Dependency on Rhino

Jse4conf uses the
[Rhino](https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Rhino)
library to parse and interpret embedded JS expressions.
Rhino was chosen for its compact size, maturity, and Java compatibility.
The first application of jse4conf is in a Java environment.

Rhino version 1.7.10 and 1.7.11 were tested.
Older versions without the newer JS function expression syntax will not work.
The JS function expression makes our config files more readable.

The core library has a `Section` class that provides functions to parse/eval JS
embedded key-values in a (sub)section.

## Dependency on JGit Config

Core jse4conf library needs only Rhino.
To demonstrate its functionality and provide better testing tool,
`Conf2JS` and `JSEConfig` use
[JGit Config parser](https://github.com/eclipse/jgit/blob/master/org.eclipse.jgit/src/org/eclipse/jgit/lib/Config.java)
to parse user config files in the JGit Config syntax.
It should be easy to support other config file syntax in future releases.
* `Conf2JS` has a main function to accept a user JS file
  and a config file with JS expressions.
  It compiles the config file to JS code, computes the JS code together
  with the other user provided JS file, and dumps the final values to a file.
* `JSEConfig` extends the JGit Config class.
  It can replace user application's Config class,
  and provide additional interpretation of JS expressions in a config file.
  Users can provide additional JS code to be evaluated together with the
  config file JS code.

## Sample Code

The `javatests/examples` directory contains several small examples
to demonstrate both capability and testing methods.

### Naming Convention

A config file's section, subsection, and key names could contain characters
not acceptable as JS variables. Jse4conf removes those characters and combines
the remaining ones into a JS *camel-case* name.
For example, in [javatests/examples/t1.conf](javatests/examples/t1.conf),
we can see patterns like:
``` shell
[plugin "my-test"]  # plugin-my-test => pluginMyTest
  useJSE = true
[Sec.two]  # Sec.two => SecTwo
  # key "p1-p2-name" => "p1P2Name"
  p1-p2-name = 'p1-p2' + '-name';
```

### Forward References

Users can keep the key-value pairs of a section in any order.
Forward reference is okay and users are encouraged to list important keys first.

In the [SampleCL example](#samplecl-example),
we listed the SampleCL keys in a *top-down* fashion.
``` text
[SampleCL]
  useJSE = ...
  authorIsSuperUser = ...
  uploaderIsSuperUser = ...
  isSuperUser = ...
  SuperUsers = ...
```

Jse4conf looks for the dependencies among keys and evaluates them
in a correct order if possible.
The `conf2js` program dumps final key values alphabetically
for easier debugging:
``` javascript
var SampleCL = {
  SuperUsers: [101, 107],
  authorIsSuperUser: true,
  isSuperUser: (x) => SuperUsers.indexOf(x.Id) >= 0,
  uploaderIsSuperUser: false,
  useJSE: true,
};
```

### Wrong Cyclic References

If a user config file contains syntax error or cyclic reference,
jse4conf cannot find a correct way to evaluate all key values.
In that case, jse4conf will output some error message in its
generated JS code.
This is best debugged by the output from `conf2js`.
For example, [javatests/examples/t3.conf](javatests/examples/t3.conf)
has an unresolvable cyclic reference:
``` shell
[T2] # unresolvable cyclic dependency
  useJSE = true
  v1 = v2 + 10
  v2 = v1 + 20
```
The `conf2js` output in
[javatests/examples/t3.conf.js.out](javatests/examples/t3.conf.js.out)
shows the error message from the Rhino JS interpreter:
``` javascript
/*
var T2 = function() {
  const useJSE = true;
  const v2 = 'v1 + 20';
  const v1 = v2 + 10;
  // ERROR: v2 = v1 + 20
  // org.mozilla.javascript.EcmaError: ReferenceError: "v1" is not defined. (v2#1)
  return {useJSE:useJSE,v1:v1,v2:v2,};
  // cycle: v1 => v2 => v1
  // cycle: v2 => v1 => v2
}();
*/
var T2 = {
  useJSE: true,
  v1: "v1 + 2010",
  v2: "v1 + 20",
};
```

Note that in this case because `v2` cannot be evaluated before `v1` is defined,
jse4conf includes an error message and treats `v2`'s value as a string.
After that, `v1` can be evaluated and it got the string value of `(v2 + 10)`,
which is probably not expected by T2's author.

### Explicit Key Orders

Jse4conf uses a simple JS tree walker to find key names used in
all key value expressions. If a key K1's value expression uses K2,
K1 depends on K2 and jse4conf will evaluate K2 before K1.

If jse4conf does not find all such dependencies or has some mistake
in this tree walker, the key evaluation order could be wrong.
Such mistakes can create wrong key values or references
to undefined key names.
To work around such errors or for any reason to enforce a specific
evaluation order, user can provide a list of key names
to tell jse4conf the (partial) evaluation order.

In [javatests/examples/t5.conf](javatests/examples/t5.conf),
there are a few examples:
* Section `T1` has a parameter `n` that was mistaken as key `n` before,
  so in `T2` a key `zOrder = [fac, n]` was used to tell jse4conf to
  evaluate `fac` before `n`.
* Section `T3` has object property references like `x.n1` and property
  definitions like `n1:6`. They were mistreated as references
  to key `n1` and caused `getN` and `xObj` being defined after `n1`.
  Such error can be fixed by a list like `zObj = [xObj, getN, n1, n2]`
  in Section `T4`.
* Section `T5` has tricky code that assign values to `n1`, `n2`, and `n3`
  depending on their evaluation order. Section `T6` gives an explicit
  order in `orderOfKeys = [n3, n2, n1]` to assign 1/2/3 to n3/n2/n1.

See current jse4conf output for `t5.conf` in
[javatests/examples/t5.conf.js.out](javatests/examples/t5.conf.js.out).

## Testing Jse4conf

* `JUnit` framework is used to test core features.
* `Conf2JS` is used to test user JS code in a source file or
   embedded in a config file following the JGit Config syntax.

See more instructions in [javatests/tests.md](./javatests/tests.md).

## Contact
jse4conf-eng@google.com
