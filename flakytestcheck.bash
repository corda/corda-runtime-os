#!/bin/bash
for i in `seq 1 100`
do
./gradlew :components:link-manager:clean
./gradlew :components:link-manager:integrationTest
if [ $? -ne 0 ]
then
exit 1
fi
done