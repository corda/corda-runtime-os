#!/bin/bash

source settings.sh
set -e

declare -a namespaces=($A_CLUSTER_NAMESPACE $B_CLUSTER_NAMESPACE $MGM_CLUSTER_NAMESPACE $APP_SIMULATOR_DB_NAMESPACE)

for namespace in ${namespaces[@]}; do
   kubectl delete ns $namespace
done

killall kubectl
