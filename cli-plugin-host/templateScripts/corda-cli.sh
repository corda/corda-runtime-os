#!/bin/sh

# get path to script
SCRIPTPATH=$(dirname "$0")

rootDir="$SCRIPTPATH/../.."
binDir="$rootDir/app/build/libs"
pluginsDir="$rootDir/build/plugins"

java  -Dpf4j.pluginsDir="$pluginsDir" -jar "$binDir/corda-cli-VERSION.jar" "$@"
