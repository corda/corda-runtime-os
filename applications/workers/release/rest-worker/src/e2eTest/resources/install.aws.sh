#!/usr/bin/env bash

set -e

aws eks update-kubeconfig --name eks-e2e-dev

telepresence quit
ps -ef  | grep 'kubectl port-forward'| grep -v grep | awk '{print $2}' | xargs kill || echo 'forward is not running'

NAMESPACE="$USER-p2p-cluster"
kubectl delete ns $NAMESPACE || echo "Name space $NAMESPACE not exists"
kubectl create ns $NAMESPACE
kubectl label ns $NAMESPACE namespace-type=corda-e2e --overwrite=true

cd ~/corda-dev-helm/

helm repo add bitnami https://charts.bitnami.com/bitnami
helm dependency build charts/corda-dev
helm repo add bitnami https://charts.bitnami.com/bitnami
helm upgrade --install prereqs -n corda \
  charts/corda-dev \
  --set 'kafka.replicaCount=1,kafka.zookeeper.replicaCount=1' \
  -n $NAMESPACE \
  --wait --timeout 600s


cd ~/corda-runtime-os/
helm install corda \
  ./charts/corda \
  -f .ci/e2eTests/corda.yaml \
  --set "bootstrap.kafka.replicas=1,kafka.sasl.enabled=false" \
  -n $NAMESPACE \
  --wait --timeout 600s


kubectl get secret -n $NAMESPACE prereqs-kafka-0-tls -o go-template='{{ index .data "ca.crt" }}' | base64 --decode > /tmp/cluster/ca.crt

telepresence connect

kubectl port-forward --namespace $NAMESPACE deployment/corda-rest-worker 8888 > /tmp/cluster/forward.8888.txt &

echo "To run the tests set environment variables to:"
echo "  CORDA_KAFKA_SSL_TRUSTSTORE_LOCATION=/tmp/cluster/ca.crt"
echo "  CORDA_KAFKA_SSL_TRUSTSTORE_TYPE=PEM"
echo "  CORDA_KAFKA_SECURITY_PROTOCOL=SSL"
echo "  CORDA_KAFKA_BOOTSTRAP_SERVERS=prereqs-kafka.$NAMESPACE:9092"
