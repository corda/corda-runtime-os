#!/bin/bash
# Settings for the P2P Deployment as Enviroment Varaibles

# Prefix the K8s namespace of each corda cluster
NAMESPACE_PREFIX="${USER//./}"

# Chart and Docker Image versions to deploy
CORDA_CHART_VERSION="^0.1.0-beta"
#DOCKER_IMAGE_VERSION=preTest-5.0.0.0-alpha-1671006298133 
DOCKER_IMAGE_VERSION=preTest-5a2e61d01
# K8s namespaces
A_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-a
B_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-cluster-b
MGM_CLUSTER_NAMESPACE=$NAMESPACE_PREFIX-mgm
APP_SIMULATOR_DB_NAMESPACE=$NAMESPACE_PREFIX-db

# RPC PORTS
A_RPC_PORT=8888
B_RPC_PORT=8889
MGM_RPC_PORT=8890

A_RPC=localhost:$A_RPC_PORT
B_RPC=localhost:$B_RPC_PORT
MGM_RPC=localhost:$MGM_RPC_PORT

A_GATEWAY_ADDRESS=corda-p2p-gateway-worker.$A_CLUSTER_NAMESPACE
A_GATEWAY_ENDPOINT=https://$A_GATEWAY_ADDRESS:8080
B_GATEWAY_ADDRESS=corda-p2p-gateway-worker.$B_CLUSTER_NAMESPACE
B_GATEWAY_ENDPOINT=https://$B_GATEWAY_ADDRESS:8080
MGM_GATEWAY_ADDRESS=corda-p2p-gateway-worker.$MGM_CLUSTER_NAMESPACE
MGM_GATEWAY_ENDPOINT=https://$MGM_GATEWAY_ADDRESS:8080

REPO_TOP_LEVEL_DIR=../../../../../
CA_DIR=$REPO_TOP_LEVEL_DIR/applications/tools/p2p-test/fake-ca/
CA_JAR=$CA_DIR/build/bin/corda-fake-ca*.jar
APP_SIMULATOR_DIR=$REPO_TOP_LEVEL_DIR/applications/tools/p2p-test/app-simulator/
APP_SIMULATOR_CHART_DIR=$APP_SIMULATOR_DIR/charts/app-simulator
APP_SIMULATOR_DB_CHART_DIR=$APP_SIMULATOR_DIR/charts/app-simulator-db

# X500Names
MGM_X500_NAME="C=GB,L=London,O=MGM"
A_X500_NAME="C=GB,L=London,O=Alice"
B_X500_NAME="C=GB,L=London,O=Bob"

MGM_HOLDING_ID_FILE=./mgmHoldingIdShortHash
