#!/usr/bin/env bash
set -o errexit -o pipefail

# Colors
NO_COLOR='\033[0m'
INFO_COLOR='\033[1;32m'
WARN_COLOR='\033[0;31m'

function info() {
  echo -e "${INFO_COLOR}"["$(date '+%F %T')"]: "${1}${NO_COLOR}"
}

i=1
#while true; do
##  info "Cleaning Previous Run [iteration ${i}]..."
##  docker rm --force postgresql 2>/dev/null || true
##  set +o errexit
##  PID=$(pgrep -f "combined-worker")
##  [[ -n "${PID}" ]] && kill -9 "${PID}" >>/dev/null
##  set -o errexit
##
##  info "Starting Combined Worker [iteration ${i}]..."
##  docker run -d --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest -c max_connections=1024
##  java -Dlog4j.configurationFile="osgi-framework-bootstrap/src/main/resources/log4j2-console.xml" -jar \
##      -Dco.paralleluniverse.fibers.verifyInstrumentation=true \
##      "applications/workers/release/combined-worker/build/bin/corda-combined-worker-5.0.0.0-SNAPSHOT.jar" \
##      --instanceId=0 -mbus.busType=DATABASE -spassphrase=password -ssalt=salt \
##      -ddatabase.user=user -ddatabase.pass=password -ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster -ddatabase.jdbc.directory="applications/workers/release/combined-worker/drivers" > ./corda.log 2>&1 &
##
##  while [[ `cat ./corda.log | grep -c "Crypto processor is set to be UP"` -lt 1 ]]; do info "Worker Not Ready, Sleeping 10 Seconds [iteration ${i}]..." && sleep 10; done;
#
#  info "Running Tests Again [iteration ${i}]..."
##  ./gradlew smokeTest --tests 'UtxoLedgerTests' --rerun-tasks
##  ./gradlew smokeTest --tests 'UtxoLedgerTests' --rerun-tasks
#  ./gradlew :components:virtual-node:sandbox-group-context-service:clean :components:virtual-node:sandbox-group-context-service:build --rerun-tasks --offline
#  ((i = i + 1))
#done

while true; do
#  info "Cleaning Previous Run [iteration ${i}]..."
#  docker rm --force postgresql 2>/dev/null || true
#
#  info "Starting Postgres SQL Database [iteration ${i}]..."
#  docker run -d --rm -p 5432:5432 --name postgresql -e POSTGRES_DB=cordacluster -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password postgres:latest -c max_connections=1024

  info "Running Tests Again [iteration ${i}]..."
#  ./gradlew :testing:message-patterns:integrationTest --tests 'StateAndEventSubscriptionIntegrationTest' --rerun-tasks
#  ./gradlew :libs:ledger:ledger-common-data:integrationTest --tests 'WireTransactionSerializerTest' --rerun-tasks
  ./gradlew :libs:crypto:crypto-impl:clean :libs:crypto:crypto-impl:test --tests 'CryptoRetryingExecutorsTests' --rerun-tasks

  ((i = i + 1))
done
