#!/bin/bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source "$SCRIPT_DIR"/settings.sh

deploy() {
   local namespace=$1

   echo Creating $namespace
   kubectl delete ns $namespace || echo ''
   kubectl create ns $namespace
   helm registry login corda-os-docker.software.r3.com -u $CORDA_ARTIFACTORY_USERNAME -p $CORDA_ARTIFACTORY_PASSWORD
   helm registry login corda-os-docker-unstable.software.r3.com -u $CORDA_ARTIFACTORY_USERNAME -p $CORDA_ARTIFACTORY_PASSWORD

   echo Installing prereqs into $namespace
   helm upgrade --install prereqs -n $namespace \
     oci://corda-os-docker.software.r3.com/helm-charts/corda-dev \
     --set image.registry="corda-os-docker.software.r3.com" \
     --set kafka.replicaCount=$KAFKA_REPLICAS,kafka.zookeeper.replicaCount=$KAFKA_ZOOKEEPER_REPLICAS \
     --render-subchart-notes \
     --timeout 10m \
     --wait

   echo Installing corda image $DOCKER_IMAGE_VERSION into $namespace
   helm upgrade --install corda -n $namespace oci://corda-os-docker-unstable.software.r3.com/helm-charts/corda \
     --set "imagePullSecrets={docker-registry-cred}" --set image.tag=$DOCKER_IMAGE_VERSION \
     --set image.registry="corda-os-docker.software.r3.com" --values "$SCRIPT_DIR"/$REPO_TOP_LEVEL_DIR/values.yaml \
     --values "$SCRIPT_DIR"/$REPO_TOP_LEVEL_DIR/debug.yaml --wait --version $CORDA_CHART_VERSION
}

if [ $# -eq 0 ]
then
  declare -a namespaces=($A_CLUSTER_NAMESPACE $B_CLUSTER_NAMESPACE $MGM_CLUSTER_NAMESPACE)
else
  namespaces=$@
fi

for namespace in ${namespaces[@]}; do
    deploy $namespace &
done

wait
