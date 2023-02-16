#!/usr/bin/env bash

set -e

aws eks update-kubeconfig --name eks-e2e-dev

telepresence quit
create_cluster() {
  BASE_IMAGE="preTest-bf256886b"
  NAMESPACE="$USER-$1"
  kubectl delete ns $NAMESPACE || echo "Name space $NAMESPACE not exists"
  kubectl create ns $NAMESPACE
  kubectl label ns $NAMESPACE namespace-type=corda-e2e --overwrite=true
  kubectl label ns $NAMESPACE branch=$USER-registration-e2e --overwrite=true

  helm upgrade --install prereqs -n corda \
    oci://corda-os-docker.software.r3.com/helm-charts/corda-dev \
    -f .ci/e2eTests/prereqs.yaml \
    -f .ci/e2eTests/prereqs-eks.yaml \
    --set 'kafka.replicaCount=1,kafka.zookeeper.replicaCount=1,kafka.offsetsTopicReplicationFactor=1,kafka.transactionStateLogReplicationFactor=1' \
    --version 0.1.0 \
    -n $NAMESPACE \
    --wait --timeout 600s

  KAFKA_PASSWORDS=$(kubectl get secret prereqs-kafka-jaas -n "${NAMESPACE}" -o go-template="{{ index .data \"client-passwords\" | base64decode }}")
  IFS=',' read -r -a KAFKA_PASSWORDS_ARRAY <<< "$KAFKA_PASSWORDS"
  kubectl create secret generic kafka-credentials -n "${NAMESPACE}" \
    --from-literal=bootstrap="${KAFKA_PASSWORDS_ARRAY[0]}" \
    --from-literal=crypto="${KAFKA_PASSWORDS_ARRAY[1]}" \
    --from-literal=db="${KAFKA_PASSWORDS_ARRAY[2]}" \
    --from-literal=flow="${KAFKA_PASSWORDS_ARRAY[3]}" \
    --from-literal=membership="${KAFKA_PASSWORDS_ARRAY[4]}" \
    --from-literal=p2pGateway="${KAFKA_PASSWORDS_ARRAY[5]}" \
    --from-literal=p2pLinkManager="${KAFKA_PASSWORDS_ARRAY[6]}" \
    --from-literal=rpc="${KAFKA_PASSWORDS_ARRAY[7]}"

  ./gradlew clean publishOSGiImage -PcompositeBuild=true
  helm install corda \
    ./charts/corda \
    -f .ci/e2eTests/corda.yaml \
    -f .ci/e2eTests/corda-eks.yaml \
    --set "image.tag=$BASE_IMAGE,bootstrap.kafka.replicas=1" \
    -n $NAMESPACE \
    --wait --timeout 600s

}


create_cluster cluster-a
create_cluster cluster-b
create_cluster cluster-mgm

telepresence connect
