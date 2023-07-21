#!/bin/bash


set -e
TESTING_DIR=$(dirname ${BASH_SOURCE[0]})
SCRIPT_DIR="$TESTING_DIR/.."

source "$SCRIPT_DIR/settings.sh"

echo '
try(var s = new java.net.ServerSocket(0);) {System.out.println(s.getLocalPort());}
/exit
' > "$WORKING_DIR"/openport.java
pids=()

clustersLines=$(kubectl get ns --no-headers -o custom-columns=":metadata.name" | grep "$NAMESPACE_PREFIX-cluster")
clusters=()
while IFS= read -r line; do
    clusters+=("$line")
done <<< "$clustersLines"

declare -A clusterPort
for cluster in "${clusters[@]}"; do
  output=$(mktemp)
  port=$(jshell "$WORKING_DIR"/openport.java)
  kubectl port-forward --namespace $cluster deployment/corda-rest-worker $port:8888 > "$output" &
  pids+=($!)
  (tail -f $output | sed "/[::1]:$port/ q") > /dev/null
  (tail -f $output | sed "/127.0.0.1:$port/ q") > /dev/null
  clusterPort[$cluster]=$port
done;

virtualNodes=()
declare -A nodesCluster
for cluster in "${clusters[@]}"; do
  port=${clusterPort[$cluster]}
  while read -r name; do
    virtualNodes+=("$name")
    nodesCluster[$name]=$cluster
  done < <( curl --fail-with-body -s -S --insecure -u admin:admin "https://localhost:$port/api/v1/virtualnode" | jq -cr '.virtualNodes | .[] | .holdingIdentity.shortHash')
done
numberOfNodes=${#nodesCluster[@]}

for ((i=0; i<$numberOfNodes; i++)); do
  echo "Checking node $i"
  shortHash="${virtualNodes[$i]}"
  cluster="${nodesCluster[$shortHash]}"
  port="${clusterPort[$cluster]}"
  members=$(
    curl --fail-with-body -s -S --insecure -u admin:admin "https://localhost:$port/api/v1/members/$shortHash" | jq '.members | length'
  )
  if [ $members != $numberOfNodes ]; then
    echo "In cluster: $cluster member: $shortHash knows about $members while we expect it to know about $numberOfNodes"
    for pid in "${pids[@]}"; do
      kill $pid
    done
    exit -1
  fi
done

for pid in "${pids[@]}"; do
  kill $pid
done

echo "Network is valid"