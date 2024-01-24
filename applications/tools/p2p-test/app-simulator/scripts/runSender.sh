#!/bin/bash

set -e
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
source "$SCRIPT_DIR/settings.sh"

calculate_peers() {
  i=0
  IDENTITIES=""
  until [ $i == $(($1-1)) ]
  do
    IDENTITIES=$IDENTITIES$2-$i\;
    i=$((i+1))
  done
  IDENTITIES=$IDENTITIES$2-$(($1-1))
  echo $IDENTITIES
}

get_db_password() {
  POSTGRES_ADMIN_PASSWORD=$(kubectl get secret --namespace $1 db-postgresql -o jsonpath="{.data.postgres-password}" | base64 --decode)
}

deploy_sender() {
  helm upgrade --install \
    app-simulator-sender $APP_SIMULATOR_CHART_DIR \
    -f "$SENDER_DETAILS_FILE" \
    $metrics_args \
    -n $1 \
    --set db.appSimulator.password=$2 \
    --set "imagePullSecrets={docker-registry-cred}" \
    --set image.tag=$DOCKER_IMAGE_VERSION \
    --set "appSimulators.sender.senderX500Names=$3" \
    --set "appSimulators.sender.peerX500Names=$4" \
    --set "appSimulators.sender.senderGroupId=$GROUP_ID" \
    --set "appSimulators.sender.peerGroupId=$GROUP_ID" \
    --set "db.appSimulator.namespace=$5"  \
    --set appSimulators.sender.replicaCount=$WORKER_REPLICAS \
    --timeout 30m00s \
    --wait &
}

echo "Starting Sender in $1 mode"

MGM_HOLDING_ID_SHORT_HASH=$(cat $MGM_HOLDING_ID_FILE)
GROUP_ID=$(curl --fail-with-body -s -S --insecure -u admin:admin -X GET https://$MGM_RPC/api/v1/members/$MGM_HOLDING_ID_SHORT_HASH | jq '.members[0].memberContext."corda.groupId"' | tr -d '"')

HELM_A_X500_NAME=$(echo $A_X500_NAME | sed 's/,/\\,/g')
HELM_B_X500_NAME=$(echo $B_X500_NAME | sed 's/,/\\,/g')

if kubectl get ns metrics-server > /dev/null 2>/dev/null ; then
  metrics_args=" -f \"$SCRIPT_DIR/app-simulator-eks.metrics.yaml\""
fi

if [ -z "$SENDER_DETAILS_FILE" ]; then
  SENDER_DETAILS_FILE="$SCRIPT_DIR"/sender.yaml
fi

if [ "$1" == "ONE_WAY" ]
then
  echo "Deploying one-way sender."
  SENDERS=$(calculate_peers $NUM_OF_MEMBERS_PER_CLUSTER_A $HELM_A_X500_NAME)
  DESTINATIONS=$(calculate_peers $NUM_OF_MEMBERS_PER_CLUSTER_B $HELM_B_X500_NAME)
  get_db_password $APP_SIMULATOR_DB_NAMESPACE_A
  deploy_sender $A_CLUSTER_NAMESPACE $POSTGRES_ADMIN_PASSWORD $SENDERS $DESTINATIONS $APP_SIMULATOR_DB_NAMESPACE
  sender_pid=$!

  wait $sender_pid
else
  echo "Deploying two-way sender."
  SENDERS=$(calculate_peers $NUM_OF_MEMBERS_PER_CLUSTER_A $HELM_A_X500_NAME)
  DESTINATIONS=$(calculate_peers $NUM_OF_MEMBERS_PER_CLUSTER_B $HELM_B_X500_NAME)
  get_db_password $APP_SIMULATOR_DB_NAMESPACE_A
  deploy_sender $A_CLUSTER_NAMESPACE $POSTGRES_ADMIN_PASSWORD $SENDERS $DESTINATIONS $APP_SIMULATOR_DB_NAMESPACE_A
  sender_a_pid=$!

  SENDERS=$(calculate_peers $NUM_OF_MEMBERS_PER_CLUSTER_B $HELM_B_X500_NAME)
  DESTINATIONS=$(calculate_peers $NUM_OF_MEMBERS_PER_CLUSTER_A $HELM_A_X500_NAME)
  get_db_password $APP_SIMULATOR_DB_NAMESPACE_B
  deploy_sender $B_CLUSTER_NAMESPACE $POSTGRES_ADMIN_PASSWORD $SENDERS $DESTINATIONS $APP_SIMULATOR_DB_NAMESPACE_B
  sender_b_pid=$!

  wait $sender_a_pid
  wait $sender_b_pid
fi
