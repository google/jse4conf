workspace(name = "jse4conf")

maven_jar(
    name = "rhino",
    artifact = "org.mozilla:rhino:1.7.11",
    sha1 = "cf04bfb7dcf7bacbf93ab7727d842f3ab5a66f0d",
)

JGIT_VERS = "5.3.1.201904271842-r"

maven_jar(
    name = "jgit",
    artifact = "org.eclipse.jgit:org.eclipse.jgit:" + JGIT_VERS,
    sha1 = "dba85014483315fa426259bc1b8ccda9373a624b",
)

maven_jar(
    name = "truth",
    artifact = "com.google.truth:truth:1.0",
    sha1 = "998e5fb3fa31df716574b4c9e8d374855e800451",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
    name = "hamcrest",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

GUAVA_VERSION = "28.0-jre"
maven_jar(
    name = "guava",
    artifact = "com.google.guava:guava:" + GUAVA_VERSION,
    sha1 = "54fed371b4b8a8cce1e94a9abd9620982d3aa54b",
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "google_bazel_common",
    strip_prefix = "bazel-common-26011657fee96a949c66500b1662c4c7288a4968",
    urls = ["https://github.com/google/bazel-common/archive/26011657fee96a949c66500b1662c4c7288a4968.zip"],
    sha256 = "4fe09d6a62b9ec0b31601c076fa4a92e13a7159d61345c09b5c7b18759f91b87",
)
