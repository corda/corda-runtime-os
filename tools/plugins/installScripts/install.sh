#!/bin/bash

zipdir=$(dirname $0)

cliHome=~/.corda/cli/

echo "Creating corda-cli dir at cliHome"
mkdir -p $cliHome

echo "Removing previous cli version if exists"
rm $cliHome/plugins/*.jar

echo "Copying files and plugins"
cp -R $zipdir/* $cliHome

echo "Creating corda-cli Script"
echo "java -Dpf4j.pluginsDir=$cliHome/plugins -jar $cliHome/corda-cli.jar \"\$@\"" > $cliHome/corda-cli.sh

chmod 755 $cliHome/corda-cli.sh


