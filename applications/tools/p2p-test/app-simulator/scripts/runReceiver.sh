#!/bin/bash

set -e
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
source "$SCRIPT_DIR/settings.sh"

MGM_HOLDING_ID_SHORT_HASH=$(cat $MGM_HOLDING_ID_FILE)
GROUP_ID=$(curl --fail-with-body -s -S --insecure -u admin:admin -X GET https://$MGM_RPC/api/v1/members/$MGM_HOLDING_ID_SHORT_HASH | jq '.members[0].memberContext."corda.groupId"' | tr -d '"')

if kubectl get ns metrics-server > /dev/null 2>/dev/null ; then
  metrics_args=" -f \"$SCRIPT_DIR/app-simulator-eks.metrics.yaml\""
fi

kubectl create ns $APP_SIMULATOR_DB_NAMESPACE

helm upgrade --install \
    -n $APP_SIMULATOR_DB_NAMESPACE \
    db $APP_SIMULATOR_DB_CHART_DIR \
    --render-subchart-notes \
    $metrics_args \
    --set appSimulators.dbSink.replicaCount=$WORKER_REPLICAS \
    --wait

POSTGRES_ADMIN_PASSWORD=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE db-postgresql -o jsonpath="{.data.postgres-password}" | base64 --decode)

kubectl port-forward --namespace $APP_SIMULATOR_DB_NAMESPACE svc/db-postgresql 5432:5432 &

echo "Starting Receiver"

helm upgrade --install \
  app-simulator $APP_SIMULATOR_CHART_DIR \
   -f "$SCRIPT_DIR"/receiver.yaml -n $B_CLUSTER_NAMESPACE \
    $metrics_args \
   --set db.appSimulator.password=$POSTGRES_ADMIN_PASSWORD \
   --set "imagePullSecrets={docker-registry-cred}" \
   --set image.tag=$DOCKER_IMAGE_VERSION \
   --set "db.appSimulator.namespace=$APP_SIMULATOR_DB_NAMESPACE"\
    --set appSimulators.receiver.topicCreation.replicationFactor=$KAFKA_REPLICATION_FACTOR \
    --set appSimulators.receiver.replicaCount=$WORKER_REPLICAS \
    --set appSimulators.dbSink.replicaCount=$WORKER_REPLICAS \
   --wait
