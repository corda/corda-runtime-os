#!/bin/sh

# get path to script
SCRIPTPATH=$(dirname "$0")

rootDir="$SCRIPTPATH/.."
binDir="$rootDir/build/cli"

java -jar "$binDir/corda-cli.jar" "$@"
