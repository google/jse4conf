#!/bin/bash -i
#
# The script generates the open source artifacts needed for uploading a library to oss sonatype.
# The artifacts includes the jar file, the sources jar file and the javadoc jar file.
# The script signs each jar and upload them to sonatype.
#
# You need to install maven before running this script:
#
# The script will ask you the passphrase of your gpg2 key.
#
# You have to also configure the repository credentials by create a settings.xml
# file in the ~/.m2/ directory and add a settings/servers/server section:
# <settings>
#   <servers>
#     <server>
#       <id>sonatype-nexus-staging</id>
#       <username>...</username>
#       <password>...</password>
#     </server>
#   </servers>
# </settings>
#
set -e

# This file is in ${jse4conf_root}/maven
pushd $(dirname "$0")/..  > /dev/null 2>&1
echo "### Build jse4conf jar files in `pwd`"

bazel build jse4conf jse4conf-javadoc.jar libjse4conf-src.jar ...
bazel test ...

pom_file=maven/pom.xml
lib_jar=bazel-bin/libjse4conf.jar
src_jar=bazel-bin/libjse4conf-src.jar
doc_jar=bazel-bin/jse4conf-javadoc.jar

group_id=com.google.jse4conf
artifact=jse4conf

if [ -z ${jse4conf_version} ]; then
  # If jse4conf_passphrase is unset, ask the user to provide it.
  echo "Enter jse4conf version: (e.g. 0.5.1)"
  read jse4conf_version
fi
version=${jse4conf_version}

if [ -z ${gpg_passphrase} ]; then
  # If gpg_passphrase is unset, ask the user to provide it.
  echo "Enter your gpg passphrase: (no echo of input)"
  read -s gpg_passphrase
fi

# library, sources, and javadoc jar files for sonatype
tmp_dir=`mktemp -d`

# library, javadoc, and source jar files for sonatype
echo "### Copying pom.xml and .jar files to ${tmp_dir}"
/bin/cp ${pom_file} ${tmp_dir}
/bin/cp ${lib_jar} ${tmp_dir}/${artifact}.jar
/bin/cp ${doc_jar} ${tmp_dir}/${artifact}-sources.jar
/bin/cp ${src_jar} ${tmp_dir}/${artifact}-javadoc.jar
/bin/ls -l ${tmp_dir}

# Use maven to sign and deploy jar, sources jar and javadoc jar to OSS sonatype
cd ${tmp_dir}

# Fix for gpg: signing failed: Inappropriate ioctl for device
export GPG_TTY=$(tty)

for i in "" sources javadoc; do
  if [ -n "$i" ]; then
    classifier="-Dclassifier=$i"
    suffix="-$i"
  else
    classifier=""
    suffix=""
  fi
  cmd="mvn gpg:sign-and-deploy-file \
    -Dfile=${artifact}${suffix}.jar \
    -DrepositoryId=sonatype-nexus-staging \
    -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2 \
    -Dpackaging=jar \
    -DgroupId=${group_id} \
    -DartifactId=${artifact} \
    -Dversion=${version} \
    -DpomFile=pom.xml $classifier"
  echo "### $cmd -Dgpg.passphrase=..."
  eval "$cmd -Dgpg.passphrase=${gpg_passphrase}"
done

rm -rf ${tmp_dir}
popd > /dev/null
