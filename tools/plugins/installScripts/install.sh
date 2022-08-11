#!/bin/bash

cliHome="~/.corda/cli/"

echo "Creating corda-cli dir at cliHome"
mkdir -p $cliHome

echo "copying files and plugins"
cp -R . $cliHome

echo "Creating corda-cli Script"
echo "java -Dpf4j.pluginsDir=$cliHome\plugins -jar corda-cli.jar $@" > cliHome\corda-cli.sh