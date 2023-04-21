#!/usr/bin/env bash

set -e


ps -ef  | grep 'kubectl port-forward'| grep -v grep | awk '{print $2}' | xargs kill || echo 'forward is not running'

rm -rf /tmp/cluster
mkdir -p /tmp/cluster
minikube stop && minikube delete
gradle --stop


minikube start --memory 8g --cpus 4
kubectl config use-context minikube
kubectl create namespace corda
kubectl config set-context --current --namespace=corda


cd ~/corda-runtime-os
eval $(minikube docker-env)
./gradlew --no-daemon publishOSGiImage -PbaseImage=docker-remotes.software.r3.com/azul/zulu-openjdk

cd ~/corda-dev-helm/

helm repo add bitnami https://charts.bitnami.com/bitnami
helm dependency build charts/corda-dev
helm upgrade --install prereqs -n corda \
  charts/corda-dev \
  --set 'kafka.replicaCount=1,kafka.zookeeper.replicaCount=1' \
  --render-subchart-notes \
  --timeout 35m \
  --wait

cd ~/corda-runtime-os

helm upgrade --install corda -n corda \
  charts/corda \
  --values ./values.yaml \
  --timeout 35m \
  --wait



kubectl port-forward --namespace corda deployment/corda-rest-worker 8888 > /tmp/cluster/forward.8888.txt &
kubectl port-forward --namespace corda services/prereqs-kafka-headless 9092 > /tmp/cluster/forward.9092.txt &

kubectl get secret -n corda prereqs-kafka-0-tls -o go-template='{{ index .data "ca.crt" }}' | base64 --decode > /tmp/cluster/ca.crt
echo "127.0.0.1 prereqs-kafka-0.prereqs-kafka-headless.corda.svc.cluster.local" > /tmp/cluster/hosts


echo "To run the tests set environment variables to:"
echo "  JDK_HOSTS_FILE=/tmp/cluster/hosts"
echo "  CORDA_KAFKA_SSL_TRUSTSTORE_LOCATION=/tmp/cluster/ca.crt"
echo "  CORDA_KAFKA_SSL_TRUSTSTORE_TYPE=PEM"
echo "  CORDA_KAFKA_SECURITY_PROTOCOL=SSL"
echo "  CORDA_KAFKA_BOOTSTRAP_SERVERS=prereqs-kafka-0.prereqs-kafka-headless.corda.svc.cluster.local:9092"
