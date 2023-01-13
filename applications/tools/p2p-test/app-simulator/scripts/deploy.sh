#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
source $SCRIPT_DIR/settings.sh

if [ $# -eq 0 ]
then
  declare -a namespaces=($A_CLUSTER_NAMESPACE $B_CLUSTER_NAMESPACE $MGM_CLUSTER_NAMESPACE)
else
  namespaces=$@
fi

for namespace in ${namespaces[@]}; do
  echo Creating $namespace
  kubectl create ns $namespace

  echo Installing prereqs into $namespace
  helm upgrade --install prereqs -n $namespace \
    oci://corda-os-docker.software.r3.com/helm-charts/corda-dev \
    --set image.registry="corda-os-docker.software.r3.com" \
    --set kafka.replicaCount=3,kafka.zookeeper.replicaCount=1 \
    --render-subchart-notes \
    --timeout 10m \
    --wait

 echo Installing corda into $namespace
 helm upgrade --install corda -n $namespace oci://corda-os-docker-unstable.software.r3.com/helm-charts/corda \
   --set "imagePullSecrets={docker-registry-cred}" --set image.tag=$DOCKER_IMAGE_VERSION \
   --set image.registry="corda-os-docker.software.r3.com" --values $SCRIPT_DIR/$REPO_TOP_LEVEL_DIR/values.yaml \
    --values $SCRIPT_DIR/$REPO_TOP_LEVEL_DIR/debug.yaml --wait --version $CORDA_CHART_VERSION
done
