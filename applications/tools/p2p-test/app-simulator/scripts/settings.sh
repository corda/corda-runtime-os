#!/bin/bash
# Settings for the P2P Deployment as Enviroment Varaibles

# Prefix the K8s namespace of each corda cluster
NAMESPACE_PREFIX="${USER//./}"

# Chart and Docker Image versions to deploy
CORDA_CHART_VERSION="^5.2.0-beta"
REPO_TOP_LEVEL_DIR=$(cd "$SCRIPT_DIR"; git rev-parse --show-toplevel)
CORDA_VERSION="$(cat $REPO_TOP_LEVEL_DIR/gradle.properties | grep cordaProductVersion | awk -F= '{print $2}' | xargs).0"
if [ -z $DOCKER_IMAGE_VERSION ]; then
  DOCKER_IMAGE_VERSION=$(curl -u $CORDA_ARTIFACTORY_USERNAME:$CORDA_ARTIFACTORY_PASSWORD  https://corda-os-docker-unstable.software.r3.com:/v2/corda-os-p2p-link-manager-worker/tags/list | jq -r -M '.["tags"] | map(select(contains("'$CORDA_VERSION'-beta"))) | sort | reverse | .[0]')
fi
#DOCKER_IMAGE_VERSION=5.0.0.0-beta-167361472154

# Uncomment to enable mutual TLS
# MTLS="Y"

RUN_MODE="ONE_WAY"

# K8s namespaces
if [ "$CLUSTER_MODE" == "SINGLE_CLUSTER" ]
then
  A_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-a
  B_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-a
  MGM_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-a
else
  A_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-a
  B_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-b
  MGM_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-mgm
fi

if [ "$RUN_MODE" == "ONE_WAY" ]
then
  APP_SIMULATOR_DB_NAMESPACE=$NAMESPACE_PREFIX-db
else
  APP_SIMULATOR_DB_NAMESPACE_A=$NAMESPACE_PREFIX-db-a
  APP_SIMULATOR_DB_NAMESPACE_B=$NAMESPACE_PREFIX-db-b
fi

#KAFKA Settings
if [ -z $KAFKA_REPLICAS ]; then
  KAFKA_REPLICAS=1
fi
if [ -z $KAFKA_ZOOKEEPER_REPLICAS ]; then
  KAFKA_ZOOKEEPER_REPLICAS=1
fi
if [ -z $KAFKA_PARTITION_COUNT ]; then
  KAFKA_PARTITION_COUNT=10
fi
if [ -z $KAFKA_REPLICATION_FACTOR ]; then
  KAFKA_REPLICATION_FACTOR=1
fi
if [ -z $WORKER_REPLICAS ]; then
  WORKER_REPLICAS=1
fi
if [ -z $CORDA_EKS_FILE ]; then
  CORDA_EKS_FILE="$SCRIPT_DIR/corda-eks-small.yaml"
fi
if [ -z $PREREQS_EKS_FILE ]; then
  PREREQS_EKS_FILE="$SCRIPT_DIR/prereqs-eks-small.yaml"
fi

# RPC PORTS
A_RPC_PORT=8888
if [ "$CLUSTER_MODE" == "SINGLE_CLUSTER" ]
then
  B_RPC_PORT=8888
  MGM_RPC_PORT=8888
else
  B_RPC_PORT=8889
  MGM_RPC_PORT=8890
fi

A_RPC=localhost:$A_RPC_PORT
B_RPC=localhost:$B_RPC_PORT
MGM_RPC=localhost:$MGM_RPC_PORT

A_GATEWAY_ADDRESS=corda-p2p-gateway-worker.$A_CLUSTER_NAMESPACE
A_GATEWAY_ENDPOINT=https://$A_GATEWAY_ADDRESS:8080
B_GATEWAY_ADDRESS=corda-p2p-gateway-worker.$B_CLUSTER_NAMESPACE
B_GATEWAY_ENDPOINT=https://$B_GATEWAY_ADDRESS:8080
MGM_GATEWAY_ADDRESS=corda-p2p-gateway-worker.$MGM_CLUSTER_NAMESPACE
MGM_GATEWAY_ENDPOINT=https://$MGM_GATEWAY_ADDRESS:8080

CA_DIR=$REPO_TOP_LEVEL_DIR/applications/tools/p2p-test/fake-ca/
CA_JAR=$CA_DIR/build/bin/corda-fake-ca*.jar
APP_SIMULATOR_DIR=$REPO_TOP_LEVEL_DIR/applications/tools/p2p-test/app-simulator/
APP_SIMULATOR_CHART_DIR=$APP_SIMULATOR_DIR/charts/app-simulator
APP_SIMULATOR_DB_CHART_DIR=$APP_SIMULATOR_DIR/charts/app-simulator-db
CORDA_CLI_DIR="$REPO_TOP_LEVEL_DIR/../corda-cli-plugin-host"

# X500Names
MGM_X500_NAME="C=GB,L=London,O=MGM"
A_X500_NAME="C=GB,L=London,O=Alice"
B_X500_NAME="C=GB,L=London,O=Bob"

NUM_OF_MEMBERS_PER_CLUSTER_A=1
NUM_OF_MEMBERS_PER_CLUSTER_B=1

WORKING_DIR="$SCRIPT_DIR/build/working"
MGM_HOLDING_ID_FILE="$WORKING_DIR"/mgmHoldingIdShortHash
