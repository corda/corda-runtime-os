#!/bin/bash

zipdir=$(dirname $0)

combinedWorkerDir=~/.corda

echo "Creating corda-combined-worker dir"
mkdir -p $combinedWorkerDir

echo "java -jar $combinedWorkerDir/corda-combined-worker.jar \"\$@\"" > $combinedWorkerDir/corda-combined-worker.sh

chmod 755 $combinedWorkerDir/corda-combined-worker.sh
