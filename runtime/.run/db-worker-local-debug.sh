#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

JAR_PATH=$SCRIPT_DIR/../applications/workers/release/db-worker/build/bin/corda-db-worker-5.0.0.0-SNAPSHOT.jar
VM_PARAMETERS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5009

SALT=$(kubectl get secret corda-db-worker -o go-template='{{ .data.salt | base64decode }}')
PASSPHRASE=$(kubectl get secret corda-db-worker -o go-template='{{ .data.passphrase | base64decode }}')
DATABASE_PASS=$(kubectl get secret prereqs-postgresql -o go-template='{{ .data.password | base64decode }}')

PROGRAM_PARAMETERS="--instance-id=2 -mbus.kafkaProperties.common.bootstrap.servers=prereqs-kafka.corda:9092 -mbus.busType=KAFKA -spassphrase=$PASSPHRASE -ssalt=$SALT -ddatabase.user=user -ddatabase.pass=$DATABASE_PASS -ddatabase.jdbc.url=jdbc:postgresql://prereqs-postgresql.corda:5432/cordacluster"

java $VM_PARAMETERS -Dlog4j.configurationFile=log4j2-console.xml -jar $JAR_PATH $PROGRAM_PARAMETERS
