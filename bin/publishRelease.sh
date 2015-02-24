#!/bin/bash

# USAGE:  ./publishRelease.sh path/to/filename.ext pt-releases/<name>/<version>/<type>/filename.ext

# type is typically "installers", "patches", "jars", etc.

set -e

creds=$HOME/.gradle/gradle.properties
if [ ! -f "$creds" ]; then
  echo "Error: missing credentials file: $creds"
  exit 1
fi

filename=$1
pub_path=$2

pub_url=`grep "^releasePublish.url=" "$creds" | sed 's/^releasePublish.url=//'`
user=`grep "^releasePublish.username=" "$creds" | sed 's/^releasePublish.username=//'`
pass=`grep "^releasePublish.password=" "$creds" | sed 's/^releasePublish.password=//'`

if [ "$pub_url" == "" -o "$user" == "" -o "$pass" == "" ]; then
  echo "Error: could not parse credentials file: $creds"
  exit 2
fi

if [ ! -f "$filename" ]; then
  echo "Artifact not found: $filename"
  exit 4
fi

if [ "$pub_path" == "" ]; then
  echo "Publish Path not provided"
  exit 4
fi

echo "Publishing $filename to ${pub_url}/${pub_path}"
md5=`openssl md5 "$filename" | sed 's/.* //'`
sha1=`openssl sha1 "$filename" | sed 's/.* //'`
curl -XPUT -L                    \
     -H "X-Checksum-Sha1: $sha1" \
     -H "X-Checksum-Md5: $md5"   \
     -u "$user:$pass"            \
      --data-binary @"${filename}"   \
     "${pub_url}/${pub_path}"

