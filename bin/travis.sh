#!/bin/bash

git fetch --unshallow
./bin/invoke-sdk.sh clean test package
