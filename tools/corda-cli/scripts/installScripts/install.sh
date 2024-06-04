#!/bin/bash

zipdir=$(dirname $0)

cliHome=~/.corda/cli/

echo "Removing previous cli version if exists"
rm -rf $cliHome

echo "Creating corda-cli dir at cliHome"
mkdir -p $cliHome

echo "Copying files and plugins"
cp -R $zipdir/* $cliHome

chmod 755 $cliHome/corda-cli.sh


