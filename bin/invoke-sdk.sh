#!/bin/bash

# ensure sdk is installed
bin/install-plugin-sdk-linux.sh

.sdk/bin/atlas-mvn "$@"
