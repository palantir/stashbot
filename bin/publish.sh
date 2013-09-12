#!/bin/bash

set -e

./bin/invoke-sdk.sh clean test package

creds=$HOME/.artifactory-credentials

version=`git describe --abbrev=12`
# TODO: if contains -gXXXX, publish snapshot?

if [ "$version" == "" ]; then
  echo "Error: unable to determine version"
  exit 3
fi

deploy_path=ivy-libs-release/com.palantir.stash/stashbot/$version/

if [ ! -f "$creds" ]; then
  echo "Error: missing credentials file: $creds"
  exit 1
fi

host=`grep "^host="     "$creds" | sed 's/^host=//' | sed 's/.pg-bd//'`
user=`grep "^user="     "$creds" | sed 's/^user=//'`
pass=`grep "^password=" "$creds" | sed 's/^password=//'`

if [ "$host" == "" -o "$user" == "" -o "$pass" == "" ]; then
  echo "Error: could not parse credentials file: $creds"
  exit 2
fi


file="stashbot-$version.jar"

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
     "https://${host}/artifactory/${deploy_path}/${file}"

