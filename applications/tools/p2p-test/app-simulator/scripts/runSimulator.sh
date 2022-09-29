#!/bin/bash

source settings.sh
set -e

MGM_HOLDING_ID_SHORT_HASH=$(cat $MGM_HOLDING_ID_FILE)
GROUP_ID=$(curl -s -S --insecure -u admin:admin -X GET https://$MGM_RPC/api/v1/members/$MGM_HOLDING_ID_SHORT_HASH | jq '.members[0].memberContext."corda.groupId"' | tr -d '"')

kubectl create ns $APP_SIMULATOR_DB_NAMESPACE

helm upgrade --install -n $APP_SIMULATOR_DB_NAMESPACE db $APP_SIMULATOR_DB_CHART_DIR --render-subchart-notes --wait

POSTGRES_ADMIN_PASSWORD=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE db-postgresql -o jsonpath="{.data.postgres-password}" | base64 --decode)

kubectl port-forward --namespace $APP_SIMULATOR_DB_NAMESPACE svc/db-postgresql 5432:5432 &

echo "Starting Receiver"

helm upgrade --install app-simulator $APP_SIMULATOR_CHART_DIR -f receiver.yaml -n $B_CLUSTER_NAMESPACE --set db.appSimulator.password=$POSTGRES_ADMIN_PASSWORD --set "imagePullSecrets={docker-registry-cred}" --set image.tag=$DOCKER_IMAGE_VERSION --set "db.appSimulator.namespace=$APP_SIMULATOR_DB_NAMESPACE"  --wait

echo "Starting Sender"

HELM_A_X500_NAME=$(echo $A_X500_NAME | sed 's/,/\\,/g')
HELM_B_X500_NAME=$(echo $B_X500_NAME | sed 's/,/\\,/g')

helm upgrade --install app-simulator $APP_SIMULATOR_CHART_DIR -f sender.yaml  -n $A_CLUSTER_NAMESPACE --set db.appSimulator.password=$POSTGRES_ADMIN_PASSWORD --set "imagePullSecrets={docker-registry-cred}" --set image.tag=$DOCKER_IMAGE_VERSION --set "appSimulators.sender.ourX500Name=$HELM_A_X500_NAME" --set "appSimulators.sender.peerX500Name=$HELM_B_X500_NAME"  --set "appSimulators.sender.ourGroupId=$GROUP_ID" --set "appSimulators.sender.peerGroupId=$GROUP_ID" --set "db.appSimulator.namespace=$APP_SIMULATOR_DB_NAMESPACE"  --wait
