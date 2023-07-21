#!/bin/bash

# Run large network scenario
# Deploy given number of clusters and onboard a given number of members in each cluster.
# To run use:
# DOCKER_IMAGE_VERSION=<IMAGE_TAG> \
# CLUSTERS_COUNT=<number of clusters> \
# MEMBER_COUNT=<number of members in each cluster> \
# ./applications/tools/p2p-test/app-simulator/scripts/large-network-testing/runScenario.sh

set -e
TESTING_DIR=$(dirname ${BASH_SOURCE[0]})
REPO_TOP_LEVEL_DIR=$(cd "$TESTING_DIR"; git rev-parse --show-toplevel)

if [ -z $CLUSTERS_COUNT ]; then
  CLUSTERS_COUNT=1
fi
if [ -z $MEMBER_COUNT ]; then
  MEMBER_COUNT=100
fi


export CLUSTERS_COUNT=$CLUSTERS_COUNT
export MEMBER_COUNT=$MEMBER_COUNT

$REPO_TOP_LEVEL_DIR/gradlew -p $REPO_TOP_LEVEL_DIR \
   :applications:tools:p2p-test:app-simulator:test --tests="*LargeNetworkTest*" --rerun
