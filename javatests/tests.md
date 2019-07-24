# Jse4conf Tests

Here are several ways to test jse4conf library and JS code in user config files.

## Table of Contents
* [Rhino Shell](#rhino-shell)
* [Config File Tests](#config-file-tests)
    * [Batch Config File Tests](#batch-config-file-tests)
* [JS Unit Tests](#js-unit-tests)
    * [JSTest.java](#jstestjava)
    * [JSFileTest.java](#jsfiletestjava)
<!-- Translation of JSTest.java and JSFileTest.java to anchor names
depends on .md file parser.  So here we explicitly added our HTML anchors. -->

## Rhino Shell

If you need to test many JS code before using them in your config files,
you can do it in the Rhino JS interpreter. First, download
[rhino-1.7.11.jar](https://mvnrepository.com/artifact/org.mozilla/rhino)
or newer file and run it like this:
``` javascript
$ java -jar rhino-1.7.11.jar
Rhino 1.7.11 2019 05 30
js> var fac = (n) => n < 2 ? 1 : fac(n-1)*n;
js> fac(5);
120
js> fac.toSource();
(n) => n < 2 ? 1 : fac(n - 1) * n
js> quit()
```

You can also put your JS code in a file and run the Rhino sell like this:
``` javascript
$ cat /tmp/x.js
a = {x:1, y:2};
print(a.toString());
print(a.toSource());
print(JSON.stringify(a));

$ java -jar rhino-1.7.11.jar /tmp/x.js
[object Object]
({x:1, y:2})
{"x":1,"y":2}
```

For other Rhino shell commands, see
[Rhino Shell](https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Rhino/Shell).


## Config File Tests

Applications of jse4conf usually contain config files with JS expressions
and those expressions are evaluated with some predefined JS variables.
Testing such config files and predefined JS variables requires a program
like `conf2js` to compile the config file and then evaluate the generated code.

In the examples directory, there is a `run.sh` script that calls `conf2js`
with different input files like this:
``` bash
  conf2js -js t1.js.in t1.conf t1.conf.js.out
```

In this example, `t1.js.in` defines JS variable `CL`:
``` javascript
  var CL = {};
  CL.branch = 'refs/master';
  CL.project = 'projectA/D1';
```

and `t1.conf` has some config (sub)section definitions like:
``` shell
[plugin "my-test"]  # pluginMyTest
  br = CL.branch
  path = CL.project + ':' + br
  useJSE = true

[S1 "s2"]  # S1-"s2" is converted to JS name S1S2
  useJSE = true
  br1 = CL.branch
```

Both files are taken by `conf2js` to generate output file `t1.conf.js.out`,
which contains the global variables defined in `t1.js.in`,
and the JS code generated from `t1.conf`.
``` javascript
var CL = {
  branch: "refs/master",
  project: "projectA/D1",
};
/*
var pluginMyTest = function() {
  const br = CL.branch;
  const path = CL.project + ':' + br;
  const useJSE = true;
  return {br:br,path:path,useJSE:useJSE,};
}();
*/
var pluginMyTest = {
  br: "refs/master",
  path: "projectA/D1:refs/master",
  useJSE: true,
};
/*
var S1S2 = function() {
  const br1 = CL.branch;
  const useJSE = true;
  return {br1:br1,useJSE:useJSE,};
}();
*/
var S1S2 = {
  br1: "refs/master",
  useJSE: true,
};
```

### Batch Config File Tests

In the BUILD file, the `examples` target calls `javatests/examples/run.sh` to
test `conf2js` with multiple input files and then compare the output with
expected files.
Jse4conf users can easily add their own test files into local `javatests/examples`.

With a pointer to the `conf2js` program, the `run.sh` script can also
run without going through the BUILD test command.
See the comments in `run.sh`.


## JS Unit Tests

In `javatests/com/google/jse4conf`, there are JUnit tests.
`JSTest.java` and `JSFileTest.java` are most useful to jse4conf users.

<a name="jstestjava"></a>
### JSTest.java

Simple JS expressions can be tested in `JSTest.java` like this:
``` java
  @Test
  public void multiInitCalls() {
    evalJS("x=10;y=20;");
    js.check("x+y", 30);
    js.check("x*y", 200);
    js.reset("x=1");
    js.check("x", 1);
    js.check("y", JS.rhinoUndefinedError("y", "<str>", 1));
  }

  @Test
  public void scriptTypes() {
    assertNull(evalJS("x=null")); // JavaScript null evals to Java null
    js.checkTypes("3>5", "Boolean", "java.lang.Boolean");
    // Rhino returns Integer if possible, otherwise a Double.
    js.checkTypes("12500", "Number", "java.lang.Integer");
    js.checkTypes("125.00", "Number", "java.lang.Integer");
    js.checkTypes("1.25", "Number", "java.lang.Double");
  }
```

<a name="jsfiletestjava"></a>
### JSFileTest.java

For longer JS code, put them in a source file that
sets up some global variable or dump some value at the end.
Then load the source file and check the final value or
the value of any global variable.
``` javascript
  $ cat test1.js
  var t1 = function() {
    const k1 = 1;
    const k2 = k1 + 1;
    const k3 = [k1, k2];
    return {
      k3: k3,
    };
  }();
  JSON.stringify(t1);
  // Expect final returned value: '{"k3":[1,2]}'

  $ cat gerrit.js
  var CL = {};
  CL.user = (email, id, name) => ({Email: email, Id: id, Name: name});
  CL.Author = CL.user('u101@g.com', 101, 'John Smith');
  CL.Branch = 'refs/beta';
```
``` java
  @Test
  public void loadTest1() throws Exception {
    // Load one JS file and check the last expression value.
    loadFileTest("test1", "test1.js", "{\"k3\":[1,2]}");
  }

  @Test
  public void gerrit() throws Exception {
    resetJSFromFile(DATA_DIR + "gerrit.js");
    js.check("CL.Author.Email", "u101@g.com");
    js.check("CL.Branch", "refs/beta");
  }
```
