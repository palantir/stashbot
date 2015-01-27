#!/bin/bash

# ensure sdk is installed
bin/install-plugin-sdk-linux.sh

if [[ "x$DOMAIN_VERSION" == "x" ]]; then
    export DOMAIN_VERSION=`git describe --dirty="-dirty" --abbrev=12`
fi

.sdk/bin/atlas-mvn "$@"
