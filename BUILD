# The core source files depend only on Rhino, not on JGit.
JSE4CONF_CORE_DEPS = ["@rhino//jar"]

JSE4CONF_CORE_SRCS = [
    "java/com/google/jse4conf/JS.java",
    "java/com/google/jse4conf/Logger.java",
    "java/com/google/jse4conf/NameVisitor.java",
    "java/com/google/jse4conf/Section.java",
]

java_library(
    name = "core",
    srcs = JSE4CONF_CORE_SRCS,
    deps = JSE4CONF_CORE_DEPS,
)

# The default jse4conf library use both Rhino and JGit Config.
JSE4CONF_DEPS = JSE4CONF_CORE_DEPS + ["@jgit//jar"]

java_library(
    name = "jse4conf",
    srcs = glob(["java/**/*.java"]),
    deps = JSE4CONF_DEPS,
)

java_binary(
    name = "conf2js",
    main_class = "com/google/jse4conf/Conf2JS",
    runtime_deps = JSE4CONF_DEPS + [":jse4conf"],
)

load(":junit.bzl", "junit_tests")

JSE4CONF_COMMON_TEST_DEPS = [
    "@guava//jar",
    "@hamcrest//jar",
    "@junit//jar",
    "@truth//jar",
]

# JS test only test Rhino JS features, not Conf2JS.
JSE4CONF_JS_TEST_UTIL_SRCS = glob([
    "javatests/**/jse4conf/JSChecker.java",
    "javatests/**/jse4conf/TestBase.java",
])

JSE4CONF_JS_TEST_UTIL_DEPS = JSE4CONF_COMMON_TEST_DEPS + JSE4CONF_CORE_DEPS + [":core"]

java_library(
    name = "js_test_utils",
    testonly = 1,
    srcs = JSE4CONF_JS_TEST_UTIL_SRCS,
    deps = JSE4CONF_JS_TEST_UTIL_DEPS,
)

junit_tests(
    name = "js_test",
    srcs = glob(["javatests/**/jse4conf/JSTest.java"]),
    deps = JSE4CONF_JS_TEST_UTIL_DEPS + [":js_test_utils"],
)

# Conf test needs Conf2JS and JGit Config.
JSE4CONF_CONF_TEST_UTIL_DEPS = JSE4CONF_COMMON_TEST_DEPS + JSE4CONF_DEPS + [":jse4conf"]

java_library(
    name = "conf_test_utils",
    testonly = 1,
    srcs = JSE4CONF_JS_TEST_UTIL_SRCS + glob(["javatests/**/jse4conf/ConfTestBase.java"]),
    deps = JSE4CONF_CONF_TEST_UTIL_DEPS,
)

junit_tests(
    name = "conf_test",
    srcs = glob(
        ["javatests/**/jse4conf/*Test.java"],
        exclude = ["javatests/**/jse4conf/JSTest.java"],
    ),
    data = glob(["javatests/data/*"]),
    jvm_flags = ["-Ddata.dir=javatests/data/"],
    deps = JSE4CONF_CONF_TEST_UTIL_DEPS + [":conf_test_utils"],
)

# The examples test calls conf2js and compare output .js files.
sh_test(
    name = "examples",
    srcs = ["javatests/examples/run.sh"],
    data = glob(["javatests/examples/*"]) + [":conf2js"],
)
