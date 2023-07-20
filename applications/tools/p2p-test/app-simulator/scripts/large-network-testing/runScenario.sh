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
SCRIPT_DIR="$TESTING_DIR/.."

if [ -z $CLUSTERS_COUNT ]; then
  CLUSTERS_COUNT=1
fi
if [ -z $MEMBER_COUNT ]; then
  MEMBER_COUNT=100
fi

reportDir="$SCRIPT_DIR/build/reports"
mkdir -p $reportDir
reportFile="$SCRIPT_DIR/build/reports/report-$CLUSTERS_COUNT-$MEMBER_COUNT.txt"
echo "Report in $reportFile"
echo "Deploying $CLUSTERS_COUNT and on boarding $MEMBER_COUNT members in each cluster" > "$reportFile"

source "$SCRIPT_DIR/settings.sh"

# Cleanup
"$SCRIPT_DIR"/tearDown.sh

# Deploy clusters
clusters=()
for ((i=1; i<=$CLUSTERS_COUNT; i++)); do
    clusters+=("$NAMESPACE_PREFIX-cluster-$i")
done
PREREQS_EKS_FILE="$TESTING_DIR"/prereqs-eks-large-network.yaml "$SCRIPT_DIR"/deploy.sh "${clusters[@]}"
echo "Created $CLUSTERS_COUNT clusters names ${clusters[@]}" >> "$reportFile"

# Prepare
rm -rf "$WORKING_DIR" \
    "$CORDA_CLI_DIR"/build/plugins \
    "$CORDA_CLI_DIR"/build/generatedScripts/ \
    "$REPO_TOP_LEVEL_DIR"/tools/plugins/build/cli/plugins \
    "$REPO_TOP_LEVEL_DIR"/testing/cpbs/calculator/build/libs/
$REPO_TOP_LEVEL_DIR/gradlew -p $REPO_TOP_LEVEL_DIR \
    :tools:plugins:package:build \
    :tools:plugins:network:build \
    :testing:cpbs:calculator:build \
    :tools:plugins:build
$CORDA_CLI_DIR/gradlew -p $CORDA_CLI_DIR build
cp $REPO_TOP_LEVEL_DIR/tools/plugins/build/cli/plugins/*.jar $CORDA_CLI_DIR/build/plugins/

# Deploy MGM
echo "Onboard MGM onto ${clusters[0]}"
$CORDA_CLI_DIR/build/generatedScripts/corda-cli.sh network onboard mgm \
   --ca "$WORKING_DIR"/ca \
   -s "$WORKING_DIR"/gp/groupPolicy.json \
   "${clusters[0]}"
echo "MGM was onboarded" >> "$reportFile"

# Deploy Members
for ((memberIndex=1; memberIndex<=MEMBER_COUNT; memberIndex++)); do
  for cluster in "${clusters[@]}"; do
      echo "Onboard member $memberIndex onto $cluster"
      $CORDA_CLI_DIR/build/generatedScripts/corda-cli.sh network onboard member \
         --cpb-file $REPO_TOP_LEVEL_DIR/testing/cpbs/calculator/build/libs/*.cpb \
         --ca "$WORKING_DIR"/ca \
         --group-policy-file "$WORKING_DIR"/gp/groupPolicy.json \
         --wait \
         $cluster
      echo "Member $memberIndex was onboarded into $cluster" >> "$reportFile"
  done
done

# Cleanup
"$SCRIPT_DIR"/tearDown.sh

echo "report saved in $reportFile"
