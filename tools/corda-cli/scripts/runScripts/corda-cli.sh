#!/bin/sh

# get path to script
jarPath="$(cd "$(dirname "$0")" && pwd)"

java -jar "$jarPath/corda-cli.jar" "$@"