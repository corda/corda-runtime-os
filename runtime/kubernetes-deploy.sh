#! /bin/bash

# Set up Corda on Kubernetes; must be run from the root of corda-runtime-os

set -e

# minikube users should do:
#   eval $(minikube docker-env)

./gradlew publishOSGiImage

# Currently the init containers are not repeatable, so ensure we start from scratch each time
kubectl delete namespace corda || echo corda namesapce already deleted?
kubectl create namespace corda

# Usual prereqs
helm install prereqs -n corda oci://registry-1.docker.io/corda/corda-dev-prereqs --timeout 10m --wait

# Corda
helm dependency build charts/corda
helm install corda -n corda charts/corda --values ./values-prereqs.yaml --wait

# Port forwarding for convenience
#nc localhost 8888
#nc localhost 5432
#kubectl port-forward -n corda deploy/corda-corda-enterprise-rest-worker 8888 &
#kubectl port-forward -n corda svc/prereqs-postgres 5432:5432 &
