#!/bin/bash

source settings.sh
set -e

declare -a namespaces=($A_CLUSTER_NAMESPACE $B_CLUSTER_NAMESPACE $MGM_CLUSTER_NAMESPACE $APP_SIMULATOR_DB_NAMESPACE)

echo $namespaces
kubectl delete ns ${namespaces[*]} --wait

killall kubectl
