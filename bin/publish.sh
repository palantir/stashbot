#!/bin/bash

set -e

./bin/invoke-sdk.sh clean test package

creds=$HOME/.gradle/gradle.properties

version=`git describe --abbrev=12 | sed -e 's/-/./g'`
# TODO: if contains -gXXXX, publish snapshot?

if [ "$version" == "" ]; then
  echo "Error: unable to determine version"
  exit 3
fi

deploy_path=com.palantir.stash/stashbot/$version

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

file=`ls target/stashbot-*.jar | sed -e 's/target\///'`

if [ ! -f "target/$file" ]; then
  echo "Artifact not found: $file"
  exit 4
fi

echo "Publishing $file"
md5=`openssl md5 "target/$file" | sed 's/.* //'`
sha1=`openssl sha1 "target/$file" | sed 's/.* //'`
curl -XPUT -L                    \
     -H "X-Checksum-Sha1: $sha1" \
     -H "X-Checksum-Md5: $md5"   \
     -u "$user:$pass"            \
      --data-binary @"target/${file}"   \
     "${pub_url}/${deploy_path}/stashbot-${version}.jar"

# publish to releases artifactory server if we are on an exact tag
if [ $(git describe --exact-match) ]; then
    echo "Detected official release, publishing"
    ./bin/publishRelease.sh target/${file} pt-releases/atlassian/stashbot/${version}/stashbot.jar
else
    echo "Not a release, not publishing"
fi


