#!/bin/bash
set -e

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
source "$SCRIPT_DIR"/settings.sh

deploy() {
   local namespace=$1

   echo Creating $namespace
   kubectl delete ns $namespace || echo ''
   kubectl create ns $namespace
   prereqs_args="--install prereqs -n $namespace  \
                oci://corda-os-docker.software.r3.com/helm-charts/corda-dev --version 0.1.0  \
                --set image.registry=\"corda-os-docker.software.r3.com\"  \
                --set kafka.replicaCount=$KAFKA_REPLICAS,kafka.zookeeper.replicaCount=$KAFKA_ZOOKEEPER_REPLICAS  \
                -f \"$PREREQS_EKS_FILE\"  \
                --render-subchart-notes  \
                --timeout 10m  \
                --wait"
   corda_args="--install corda -n $namespace oci://corda-os-docker.software.r3.com/helm-charts/release/os/5.2/corda \
              --set imagePullSecrets={docker-registry-cred} --set image.tag=$DOCKER_IMAGE_VERSION \
              --set image.registry=corda-os-docker.software.r3.com --values $REPO_TOP_LEVEL_DIR/values.yaml \
              --set bootstrap.kafka.partitions=$KAFKA_PARTITION_COUNT \
              --set bootstrap.kafka.replicas=$KAFKA_REPLICATION_FACTOR \
              -f \"$CORDA_EKS_FILE\" \
              --set workers.p2pGateway.replicaCount=$WORKER_REPLICAS \
              --set workers.p2pLinkManager.replicaCount=$WORKER_REPLICAS \
              --values $REPO_TOP_LEVEL_DIR/debug.yaml --wait --version $CORDA_CHART_VERSION"
   if kubectl get ns metrics-server > /dev/null 2>/dev/null ; then
     prereqs_args+=" -f \"$SCRIPT_DIR/prereqs-eks.metrics.yaml\""
     corda_args+=" -f \"$SCRIPT_DIR/corda-eks.metrics.yaml\""
   fi

   echo Installing prereqs into $namespace
   helm upgrade ${prereqs_args}

   echo Installing corda image $DOCKER_IMAGE_VERSION into $namespace
   helm upgrade ${corda_args}
}

helm registry login corda-os-docker.software.r3.com -u $CORDA_ARTIFACTORY_USERNAME -p $CORDA_ARTIFACTORY_PASSWORD
helm registry login corda-os-docker-unstable.software.r3.com -u $CORDA_ARTIFACTORY_USERNAME -p $CORDA_ARTIFACTORY_PASSWORD

if [ $# -eq 0 ]
then
  # Deploy a smaller cluster for the MGM
  WORKER_REPLICAS=1 KAFKA_REPLICATION_FACTOR=1 KAFKA_REPLICAS=1 KAFKA_ZOOKEEPER_REPLICAS=1 KAFKA_PARTITION_COUNT=10 deploy $MGM_CLUSTER_NAMESPACE &
  declare -a namespaces=($A_CLUSTER_NAMESPACE $B_CLUSTER_NAMESPACE)
else
  namespaces=$@
fi

for namespace in ${namespaces[@]}; do
    deploy $namespace &
done

wait
