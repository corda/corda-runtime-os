#!/usr/bin/env bash

set -e

aws eks update-kubeconfig --name eks-e2e-dev

telepresence quit
create_cluster() {
  BASE_IMAGE="preTest-bf256886b"
  NAMESPACE="$USER-$1"
  echo "Trying namespace $NAMESPACE"
  kubectl delete ns $NAMESPACE || echo "Name space $NAMESPACE not exists"
  kubectl create ns $NAMESPACE
  kubectl label ns $NAMESPACE namespace-type=corda-e2e --overwrite=true
  kubectl label ns $NAMESPACE branch=$USER-registration-e2e --overwrite=true

  helm upgrade --install prereqs -n corda \
    oci://corda-os-docker.software.r3.com/helm-charts/corda-dev \
    --set 'kafka.replicaCount=1,kafka.zookeeper.replicaCount=1' -n $NAMESPACE --wait --timeout 600s

  ./gradlew clean publishOSGiImage -PcompositeBuild=true
  helm install corda ./charts/corda -f .ci/e2eTests/corda.yaml --set "image.tag=$BASE_IMAGE,bootstrap.kafka.replicas=1,kafka.sasl.enabled=false" -n $NAMESPACE --wait

}


create_cluster cluster-a
create_cluster cluster-b
create_cluster cluster-mgm

telepresence connect
