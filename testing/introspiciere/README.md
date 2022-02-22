# Welcome to the Introspiciere project

This document is the starting point for the Introspiciere project. It will guide you through the architecture and how to
do the basic operations.

We try to keep these docs in sync with the project, but be aware that might not always be the case.

## What is Introspiciere?

Introspiciere is a simple service to help devs and testers to interact with Kafka. Introspiciere means `introspect` in
latin. With Introspiciere you can do basic operations on Kafka to help you do your testing and exploratory testing. For
now you can create topics and send and read messages. We will iteratively keep adding features as we find new use cases.
All functionality can be executed manually [introspiciere-cli](`introspiciere-cli/README.md`) and
programmatically [introspiciere-junit](`introspiciere-junit/README.md`).

## Architecture

![Lucidchart](https://lucid.app/publicSegments/view/a3b5e5e3-e3ad-489d-a980-e19b7af11732/image.png)

The diagram can
be [found in Lucidchart](https://lucid.app/lucidchart/f1c5823f-7388-42ad-a8e7-173bd36dd162/edit?invitationId=inv_f219acc0-48c7-48de-9cc0-e8c5d325d644)

### Public modules

* [introspiciere-cli](introspiciere-cli/README.md) - Exposes the functionality in the form of a CLI. In the future it
  will be replaced and integrated in the `corda-cli` as a plugin.
* [introspiciere-junit](introspiciere-junit/README.md) - Exposes the functionality as a programmatic API and in the form
  of a few JUnit extensions to manage the lifecycle of the resources.
* [introspiciere-server](introspiciere-server/README.md) - Exposes the core use-cases in the form of a REST server.

### Internal modules

* [introspiciere-client](introspiciere-client/README.md) - Http client to simplify communication with the server.
* [introspiciere-payloads](introspiciere-payloads/README.md) - Module with the http payloads.
* [introspiciere-domain](introspiciere-domain/README.md) - Module with common functionality and data classes.
* [introspiciere-core](introspiciere-core/README.md) - Module with the actual funtionality.

## How to run the tests

Until we have the necessary infrastructure, you need to start a local Kafka in your machine to run the integration
tests.

**How to run integration tests from the different modules:**

1. Deploy a Kafka using the instructions below
2. Go to [introspiciere-junit](introspiciere-junit/README.md) and run the integration tests.

**How to run functionality with the CLI:**

1. Deploy a Kafka using the instructions below
2. Go to [introspiciere-server](introspiciere-server/README.md) and follow the instructions to deploy the service in
   K8s.
3. Go to [introspiciere-cli](introspiciere-cli/README.md) and follow the instructions.

### Deploy Kafka with Strimzi in minikube

These instructions have been extracted from the official Strimzi docs. This is just a cheatsheet to speed up deploying
of a Kafka locally in your machine to run tests. It might be out of sync. Refer to the official docs for more info:

* https://strimzi.io/quickstarts/
* https://strimzi.io/blog/2019/04/23/accessing-kafka-part-2/

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

# 7. Deploy kafka and wait until ready
kubectl apply -f kafka-persistent-single.yaml -n kafka 
kubectl wait kafka/my-cluster --for=condition=Ready --timeout=300s -n kafka 

# 8. Test sending message to kafka and consuming it to ensure you can connect from your machine to Kafka 
# Install kafka-console-[producer|consumer] using brew in MacOS: `brew install kafka`
BROKER_LIST=`minikube ip`:`kubectl get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.spec.ports[0].nodePort}' -n kafka`
kafka-console-producer --broker-list $BROKER_LIST --topic topic1
kafka-console-consumer --topic topic1 --from-beginning --bootstrap-server $BROKER_LIST # From different terminal
```

> When restarting minikube, delete the namespace and re-deploy Kafka again. Otherwise you might find weird behaviour.