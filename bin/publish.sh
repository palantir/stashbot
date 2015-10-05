#!/bin/bash

set -e

./bin/invoke-sdk.sh clean test package

file_prefix="stashbot-"
deploy_prefix="com.palantir.stash/stashbot"
release_prefix="pt-releases/atlassian/stashbot"
release_filename="stashbot.jar"

creds=$HOME/.gradle/gradle.properties

# version is set by maven using domain version semantics now
version=`echo target/$file_prefix*.jar | sed -e "s/target\/$file_prefix//" | sed -e 's/.jar$//' | sed -e 's/\//-/g'`
if grep -q 'release-' <<< "$version"; then
    version=`sed -e 's/^release-//g' <<< "$version"`
    release="true"
fi

if [ "$version" == "" ]; then
  echo "Error: unable to determine version"
  exit 3
fi

deploy_path=$deploy_prefix/$version

if [ ! -f "$creds" ]; then
  echo "Error: missing credentials file: $creds"
  exit 1
fi

pub_url=`grep "^palantirPublish.releaseUrl=" "$creds" | sed 's/^palantirPublish.releaseUrl=//' | sed 's/.pg-bd//'`
user=`grep "^palantirPublish.username=" "$creds" | sed 's/^palantirPublish.username=//'`
pass=`grep "^palantirPublish.password=" "$creds" | sed 's/^palantirPublish.password=//'`

if [ "$pub_url" == "" -o "$user" == "" -o "$pass" == "" ]; then
  echo "Error: could not parse credentials file: $creds"
  exit 2
fi

file=`ls target/$file_prefix*.jar | sed -e 's/target\///'`

if [ ! -f "target/$file" ]; then
  echo "Artifact not found: $file"
  exit 4
fi

echo "Publishing $file"
md5=`openssl md5 "target/$file" | sed 's/.* //'`
sha1=`openssl sha1 "target/$file" | sed 's/.* //'`
curl -XPUT -L -k                 \
     -H "X-Checksum-Sha1: $sha1" \
     -H "X-Checksum-Md5: $md5"   \
     -u "$user:$pass"            \
      --data-binary @"target/${file}"   \
      "${pub_url}/${deploy_path}/${file_prefix}${version}.jar"

# publish to releases as well if version matches a release pattern - which is "^\d+\.\d+\.\d+.*"
if git describe --exact-match >/dev/null 2>/dev/null; then
    echo "Detected version that looks like an official release, publishing"
    ./bin/publishRelease.sh target/${file} $release_prefix/$version/$release_filename
else
    echo "Not a release, not publishing"
fi


