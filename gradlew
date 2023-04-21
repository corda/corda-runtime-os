#!/bin/bash

set -e

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
"$SCRIPT_DIR"/runtime/gradlew -p "$SCRIPT_DIR"/runtime "$@"

