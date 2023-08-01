#!/bin/bash

set -e
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
source "$SCRIPT_DIR/settings.sh"

echo "Starting Sender"

MGM_HOLDING_ID_SHORT_HASH=$(cat $MGM_HOLDING_ID_FILE)
GROUP_ID=$(curl --fail-with-body -s -S --insecure -u admin:admin -X GET https://$MGM_RPC/api/v1/members/$MGM_HOLDING_ID_SHORT_HASH | jq '.members[0].memberContext."corda.groupId"' | tr -d '"')

POSTGRES_ADMIN_PASSWORD=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE db-postgresql -o jsonpath="{.data.postgres-password}" | base64 --decode)

HELM_A_X500_NAME=$(echo $A_X500_NAME | sed 's/,/\\,/g')
HELM_B_X500_NAME=$(echo $B_X500_NAME | sed 's/,/\\,/g')

if kubectl get ns metrics-server > /dev/null 2>/dev/null ; then
  metrics_args=" -f \"$SCRIPT_DIR/app-simulator-eks.metrics.yaml\""
fi

if [ -z "$SENDER_DETAILS_FILE" ]; then
  SENDER_DETAILS_FILE="$SCRIPT_DIR"/sender.yaml
fi

helm upgrade --install \
  app-simulator-sender $APP_SIMULATOR_CHART_DIR \
  -f "$SENDER_DETAILS_FILE" \
  $metrics_args \
  -n $A_CLUSTER_NAMESPACE \
  --set db.appSimulator.password=$POSTGRES_ADMIN_PASSWORD \
  --set "imagePullSecrets={docker-registry-cred}" \
  --set image.tag=$DOCKER_IMAGE_VERSION \
  --set "appSimulators.sender.ourX500Name=$HELM_A_X500_NAME" \
  --set "appSimulators.sender.peerX500Name=$HELM_B_X500_NAME" \
  --set "appSimulators.sender.ourGroupId=$GROUP_ID" \
  --set "appSimulators.sender.peerGroupId=$GROUP_ID" \
  --set "db.appSimulator.namespace=$APP_SIMULATOR_DB_NAMESPACE"  \
  --set appSimulators.sender.replicaCount=$WORKER_REPLICAS \
  --timeout 30m00s \
  --wait
