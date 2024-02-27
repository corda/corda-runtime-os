#!/bin/bash

set -e
SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
source "$SCRIPT_DIR/settings.sh"

deploy_database() {
  kubectl create ns $1

  helm upgrade --install \
      -n $1 \
      db $APP_SIMULATOR_DB_CHART_DIR \
      --render-subchart-notes \
      $metrics_args \
      --set appSimulators.dbSink.replicaCount=$WORKER_REPLICAS \
      --wait

  POSTGRES_ADMIN_PASSWORD=$(kubectl get secret --namespace $1 db-postgresql -o jsonpath="{.data.postgres-password}" | base64 --decode)

  kubectl port-forward --namespace $1 svc/db-postgresql $2:5432 &
}

deploy_receiver() {
  helm upgrade --install \
      app-simulator $APP_SIMULATOR_CHART_DIR \
       -f "$SCRIPT_DIR"/receiver.yaml -n $1 \
        $metrics_args \
       --set db.appSimulator.password=$2 \
       --set "imagePullSecrets={docker-registry-cred}" \
       --set image.tag=$DOCKER_IMAGE_VERSION \
       --set "db.appSimulator.namespace=$3"\
        --set appSimulators.receiver.topicCreation.replicationFactor=$KAFKA_REPLICATION_FACTOR \
        --set appSimulators.receiver.replicaCount=$WORKER_REPLICAS \
        --set appSimulators.dbSink.replicaCount=$WORKER_REPLICAS \
       --wait
}

MGM_HOLDING_ID_SHORT_HASH=$(cat $MGM_HOLDING_ID_FILE)
GROUP_ID=$(curl --fail-with-body -s -S --insecure -u admin:admin -X GET https://$MGM_RPC/api/v1/members/$MGM_HOLDING_ID_SHORT_HASH | jq '.members[0].memberContext."corda.groupId"' | tr -d '"')

if kubectl get ns metrics-server > /dev/null 2>/dev/null ; then
  metrics_args=" -f \"$SCRIPT_DIR/app-simulator-eks.metrics.yaml\""
fi

echo "Starting Receiver in $1 mode"

if [ $1 == "ONE_WAY" ]
then
  deploy_database $APP_SIMULATOR_DB_NAMESPACE 5432
  POSTGRES_ADMIN_PASSWORD=$POSTGRES_ADMIN_PASSWORD

  echo "Deploying receiver on one cluster: $B_CLUSTER_NAMESPACE"
  deploy_receiver $B_CLUSTER_NAMESPACE $POSTGRES_ADMIN_PASSWORD $APP_SIMULATOR_DB_NAMESPACE
else
  deploy_database $APP_SIMULATOR_DB_NAMESPACE_A 5432
  POSTGRES_ADMIN_PASSWORD_A=$POSTGRES_ADMIN_PASSWORD
  deploy_database $APP_SIMULATOR_DB_NAMESPACE_B 5434
  POSTGRES_ADMIN_PASSWORD_B=$POSTGRES_ADMIN_PASSWORD

  echo "Deploying receiver on two clusters: $A_CLUSTER_NAMESPACE, $B_CLUSTER_NAMESPACE"
  echo "Deploying first receiver"
  deploy_receiver $B_CLUSTER_NAMESPACE $POSTGRES_ADMIN_PASSWORD_B $APP_SIMULATOR_DB_NAMESPACE_B
  echo "Deploying second receiver"
  deploy_receiver $A_CLUSTER_NAMESPACE $POSTGRES_ADMIN_PASSWORD_A $APP_SIMULATOR_DB_NAMESPACE_A
fi
