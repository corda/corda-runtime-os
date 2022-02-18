# Introspiciere-junit

# How to start Kafka with Strimzi in Minikube to run tests

These instructions have been extracted from the official Strimzi docs. This is just a cheatsheet to speed up deploying
a Kafka locally in your machine to run tests, and it might be out of sync. Refer to the official docs for more info:

* https://strimzi.io/quickstarts/
* https://strimzi.io/blog/2019/04/23/accessing-kafka-part-2/

## Simple kafka

These instructions only need to be executed once.

```shell
# 1. Start minikube
minikube start --memory=4096 # 2GB default memory isn't always enough

# 2. Create namespace
kubectl create namespace kafka

# 3. Install Strimzi
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka

# 4. Create kafka dir
mkdir kafka && cd kafka

# 5. Download kafka-persistent-single.yaml deployment
curl -O -J https://strimzi.io/examples/latest/kafka/kafka-persistent-single.yaml

# 6. Add nodeport to the deployment
#     listeners:
#          [...]
#          - name: external
#            port: 9094
#            type: nodeport
#            tls: false
#     config:
#         [...]

# 7. Deploy kafka
kubectl apply -f kafka-persistent-single.yaml -n kafka 

# 8. Wait until ready
kubectl wait kafka/my-cluster --for=condition=Ready --timeout=300s -n kafka 

# 9. Send message to kafka
BROKER_LIST=`minikube ip`:`kubectl get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.spec.ports[0].nodePort}' -n kafka`
kafka-console-producer --broker-list $BROKER_LIST --topic topic1

# 10. Consume message from kafka. From different terminal
BROKER_LIST=`minikube ip`:`kubectl get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.spec.ports[0].nodePort}' -n kafka`
kafka-console-consumer --topic topic1 --from-beginning --bootstrap-server $BROKER_LIST
```
