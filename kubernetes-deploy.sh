#! /bin/bash

# Set up Corda on Kubernetes; must be run from the root of corda-runtime-os

set -e

./gradlew publishOSGiImage

# Currently the init containers are not repeatble, so ensure we start from scratch each time
kubectl delete namespace corda
kubectl create namespace corda

# Usual prereqs
helm install prereqs -n corda oci://registry-1.docker.io/corda/corda-dev-prereqs --timeout 10m --wait

# Corda
helm dependency build charts/corda
helm upgrade --install corda -n corda charts/corda \
  --values ../corda-runtime-os/values-prereqs.yaml \
  --wait

# Port forwarding for convenience
#nc localhost 8888
#nc localhost 5432
#kubectl port-forward -n corda deploy/corda-corda-enterprise-rest-worker 8888 &
#kubectl port-forward -n corda svc/prereqs-postgres 5432:5432 &
