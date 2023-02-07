#!/usr/bin/env bash

set -e

aws eks update-kubeconfig --name eks-e2e

ps -ef  | grep 'kubectl port-forward'| grep -v grep | awk '{print $2}' | xargs kill || echo 'forward is not running'

NAMESPACE=$1

telepresence quit

rm -rf /tmp/cluster
mkdir -p /tmp/cluster

SASL_SECRET=$(kubectl get secret -n $NAMESPACE prereqs-kafka-jaas -o=jsonpath='{.data.client-passwords}' | base64 --decode)
kubectl get secret -n $NAMESPACE prereqs-kafka-0-tls -o go-template='{{ index .data "ca.crt" }}' | base64 --decode > /tmp/cluster/ca.crt
printf "KafkaClient {\n org.apache.kafka.common.security.scram.ScramLoginModule required\n username=\"user\"\n password=\"$SASL_SECRET\";\n};\n" > /tmp/cluster/jaas.conf



telepresence connect

kubectl port-forward --namespace $NAMESPACE deployment/corda-rpc-worker 8888 > /tmp/cluster/forward.8888.txt &

echo "To run the tests set environment variables to:"
echo "  CORDA_KAFKA_SSL_TRUSTSTORE_LOCATION=/tmp/cluster/ca.crt"
echo "  CORDA_KAFKA_SSL_TRUSTSTORE_TYPE=PEM"
echo "  CORDA_KAFKA_SECURITY_PROTOCOL=SASL_SSL"
echo "  CORDA_KAFKA_SASL_MECHANISM=SCRAM-SHA-256"
echo "  CORDA_KAFKA_BOOTSTRAP_SERVERS=prereqs-kafka.$NAMESPACE:9092"
echo "  JAVA_SECURITY_AUTH_LOGIN_CONFIG=/tmp/cluster/jaas.conf"
