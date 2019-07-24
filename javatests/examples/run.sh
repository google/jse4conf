#!/bin/bash

# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script is normally called by the command:
#   bazel test conf2js_examples
#
# It calls conf2js with a set of test input files and checks if the output
# files have the expected content. For each test, a .js file and a .conf file
# are taken from the $SRCDIR, generated output file is put in $OUTDIR and then
# compared with the expected output in the $SRCDIR.
# For example, test input t1.js and t2.conf are run like this:
#   $CONF2JS -js $SRCDIR/t1.js $SRCDIR/t1.conf $OUTDIR/t1.out.js
#   diff -U3 $SRCDIR/t1.out.js $OUTDIR/t1.out.js
#
# When called by bazel, CONF2JS, SRCDIR, and OUTDIR are all created by bazel.
# They are under .../bazel-out/.../conf2js_examples.runfiles/...

# After the bazel test command is run, to fix or add only the test files,
# this script can be run alone, with environment variables defined for
# CONF2JS, SRCDIR and OUTDIR. Default SRCDIR is current working directory,
# and default OUTDIR is /tmp.

function check_vars()
{
  if [ "${TEST_SRCDIR}" != "" ]; then
    # Assume running from bazel or blaze test conf2js_examples.
    # Path to conf2js and test source files are different in bazel and blaze.
    # Find those files and truncate the long path to relative path to CWD.
    CWD=`pwd`
    echo "### TEST_SRCDIR = ${TEST_SRCDIR}"
    echo "### CWD = $CWD"
    CONF2JS=`find ${TEST_SRCDIR} -name conf2js | sed -e "s%$CWD%.%"`
    SRCDIR=`find ${TEST_SRCDIR} -name project.config | sed -e "s%$CWD%.%"`
    SRCDIR=`dirname $SRCDIR`
    OUTDIR=${TEST_TMPDIR}
  else
    # Assume running alone, after bazel built the conf2js runfiles.
    if [ "${CONF2JS}" == "" ]; then
      echo "### ERROR: CONF2JS is undefined. It should point to conf2js, e.g.,"
      echo "###    ../../../../../blaze-out/k8-fastbuild/bin/.../conf2js"
      echo "### or ../../bazel-out/k8-fastbuild/bin/conf2js"
      exit 1
    fi
    if ! [ -x ${CONF2JS} ]; then
      echo "### ERROR: CONF2JS is not executable at: $CONF2JS"
      exit 1
    fi
    # Default test source and output directories.
    SRCDIR=${SRCDIR:-.}
    OUTDIR=${OUTDIR:-/tmp}
  fi

  echo "### CONF2JS = ${CONF2JS}"
  echo "### SRCDIR = ${SRCDIR}"
  echo "### OUTDIR = ${OUTDIR}"
  if ! [ -d $OUTDIR ]; then
    echo "### Cannot find output directory: $OUTDIR"
    exit 1
  fi
}

function run_test()
{
  local JSFile=$SRCDIR/$1
  local ConfFile=$SRCDIR/$2
  local ExpectedFile=$SRCDIR/$3
  local OutFile=$OUTDIR/$3
  local TestName="\$CONF2JS -js \$SRCDIR/$1 \$SRCDIR/$2 \$OUTDIR/$3"
  echo "### running test $TestName"
  $CONF2JS -js $JSFile $ConfFile $OutFile
  result=$?
  if [ "$result" == "0" ]; then
    echo "### diff -U3 \$SRCDIR/$3 \$OUTDIR/$3"
    diff -U3 $ExpectedFile $OutFile
    result=$?
  fi
  if [ "$result" != "0" ]; then
    echo "### Test $TestName failed."
    if [ "/tmp/$3" != "$OutFile" ]; then
      echo "### BEGIN dump $OutFile"
      cat $OutFile
      echo "### END dump $OutFile"
    fi
    FAIL="${FAIL}${LF}FAIL: $TestName"
  else
    PASS="${PASS}${LF}PASS: $TestName"
  fi
}

LF=$'\n'
PASS=""
FAIL=""

check_vars

# Add more test cases here:
run_test t1.js.in t1.conf t1.conf.js.out

# t2.conf and t3.conf are run with an empty t0.js
run_test t0.js.in t2.conf t2.conf.js.out
run_test t0.js.in t3.conf t3.conf.js.out

run_test t4.js.in t4.conf t4.conf.js.out
run_test t0.js.in t5.conf t5.conf.js.out

run_test gerrit.js.in project.config project.config.js.out

# After all tests, report the PASS and FAIL tests:
echo "$PASS"
if [ "$FAIL" != "" ]; then
  echo "$FAIL"
  exit 1
fi
