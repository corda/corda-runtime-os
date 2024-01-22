#!/bin/bash

# Run horizontal scaling scenarios
# Deploy a cluster with a given number of instances and start sending more and more messages until it reaches a
# latency larger than one.
# To run use:
# DOCKER_IMAGE_VERSION<IMAGE_TAG>.  ./applications/tools/p2p-test/app-simulator/scripts/horizontal-scaling-testing/runScenario.sh

set -e
TESTING_DIR=$(dirname ${BASH_SOURCE[0]})
SCRIPT_DIR="$TESTING_DIR/.."


if [ -z $1 ]; then
  scenario_file="$TESTING_DIR"/scenarios/a.json
else
  scenario_file=$1
fi
if [ ! -f "$scenario_file" ]; then
    echo "$scenario_file does not exist."
    exit -1
fi
scenario=$(cat "$scenario_file")
reportDir="$SCRIPT_DIR/build/reports"
mkdir -p $reportDir
filename=$(basename  $scenario_file .json)
reportFile="$SCRIPT_DIR/build/reports/report-$filename.txt"
echo "Report in $reportFile"

echo "Running scenario: \"$(echo "$scenario" | jq -r '.name')\""
export KAFKA_REPLICAS=$(echo "$scenario" | jq -r '.kafkaBrokers')
export WORKER_REPLICAS=$(echo "$scenario" | jq -r '.workerReplicas')
export KAFKA_REPLICATION_FACTOR=$(echo "$scenario" | jq -r '.replicationFactor')
export KAFKA_PARTITION_COUNT=$(echo "$scenario" | jq -r '.partitionCount')
echo "$(echo "$scenario" | jq -r '.name')" > "$reportFile"
echo "---" >> "$reportFile"
echo "$WORKER_REPLICAS Link manager instances" >> "$reportFile"
echo "$WORKER_REPLICAS Gateway instances" >> "$reportFile"
echo "$KAFKA_REPLICAS Kafka brokers" >> "$reportFile"
echo "$WORKER_REPLICAS simulator in RECEIVER mode" >> "$reportFile"
echo "$WORKER_REPLICAS simulator in DB_SINK mode" >> "$reportFile"
echo "$WORKER_REPLICAS simulator in SENDER mode" >> "$reportFile"
echo "1 Zookeeper instance" >> "$reportFile"
echo "1 PostgreSQL instance" >> "$reportFile"
echo "$KAFKA_PARTITION_COUNT partitions per topic (replication factor = $KAFKA_REPLICATION_FACTOR)" >> "$reportFile"

export CORDA_EKS_FILE="$SCRIPT_DIR/corda-eks-large.yaml"
export PREREQS_EKS_FILE="$SCRIPT_DIR/prereqs-eks-large.yaml"
source "$SCRIPT_DIR/settings.sh"

if [ "$RUN_MODE" == "ONE_WAY" ]
then
  echo "One way load scenario will be generated."
else
  echo "Two way load scenario will be generated."
fi

echo "Tearing down previous clusters"
"$SCRIPT_DIR"/tearDown.sh
echo "Deploying clusters"
"$SCRIPT_DIR"/deploy.sh
echo "Onboarding clusters"
"$SCRIPT_DIR"/onBoardCluster.sh
echo "Deploying receiver in $RUN_MODE mode"
"$SCRIPT_DIR"/runReceiver.sh "$RUN_MODE"
if [ "$RUN_MODE" == "ONE_WAY" ]
then
  dbPassword=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE-db db-postgresql -o jsonpath="{.data.postgres-password}" | base64 -d)
else
  dbPasswordA=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE_A db-postgresql -o jsonpath="{.data.postgres-password}" | base64 -d)
  dbPasswordB=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE_B db-postgresql -o jsonpath="{.data.postgres-password}" | base64 -d)
fi

count_sent() {
  echo $(kubectl exec -n $1 db-postgresql-0 -- env PGPASSWORD=$2 psql -U postgres -d app_simulator -c "select COUNT(*) from sent_messages;" -t 2>/dev/null | xargs)
}

count_received() {
  echo $(kubectl exec -n $1 db-postgresql-0 -- env PGPASSWORD=$2 psql -U postgres -d app_simulator -c "select COUNT(*) from received_messages;" -t  2>/dev/null | xargs)
}

calculate_latency() {
  echo $(kubectl exec -n $1 db-postgresql-0 -- env PGPASSWORD=$2 psql -U postgres -d app_simulator -c "SELECT AVG(delivery_latency_ms)/1000.0 FROM received_messages WHERE sent_timestamp > '$3' AND sent_timestamp < '$4';" -t | xargs)
}

write_report_file() {
  kubectl exec -n $1 db-postgresql-0 \
     -- env PGPASSWORD=$2 \
     psql -U postgres -d app_simulator -A -F", "\
     -c "select
          TO_CHAR(to_timestamp(floor((extract('epoch' from rm.sent_timestamp) / 30 )) * 30) at time zone 'utc', 'HH:MI:SS') as time_window,
          count(rm.delivery_latency_ms) as total_messages,
          count(rm.delivery_latency_ms) / 30 as throughput,
          max(rm.delivery_latency_ms) / 1000.0 as max_latency,
          min(rm.delivery_latency_ms)/ 1000.0 as min_latency,
          avg(rm.delivery_latency_ms)/ 1000.0 as average_latency,
          (percentile_disc(0.99) within group (order by rm.delivery_latency_ms))/ 1000.0 as p99_latency
         from received_messages rm
         group by time_window
         order by time_window asc
  ;" >> "$reportFile"
}

run_sender() {
  echo "Running use case with $batchSize batch size and $totalNumberOfMessages messages"
  echo "---" >> "$reportFile"
  start=$(date -u '+%Y-%m-%d %H:%M:%S')
  echo $start >> "$reportFile"
  echo "totalNumberOfMessages: $totalNumberOfMessages" >> "$reportFile"
  echo "interBatchDelay: $interBatchDelay" >> "$reportFile"
  echo "batchSize: $batchSize" >> "$reportFile"
  export senderDetailsFile=$(mktemp)

  echo '{}' \
    | jq ".appSimulators.sender.totalNumberOfMessages=$totalNumberOfMessages" \
    | jq ".appSimulators.sender.interBatchDelay=\"$interBatchDelay\"" \
    | jq ".appSimulators.sender.batchSize=$batchSize" \
    | jq '.db.appSimulator.user="postgres"' \
    | jq '.appSimulators.sender.enabled=true' \
    > $senderDetailsFile
  echo "Running sender in $RUN_MODE mode..."
  SENDER_DETAILS_FILE=$senderDetailsFile "$SCRIPT_DIR"/runSender.sh "$RUN_MODE"
  rm "$senderDetailsFile"
  echo "Waiting for messages"
  stop="no"
  until [[ "$stop" == "yes" ]];  do
    echo 'Waiting...'
    sleep 1
    echo 'Checking how many messages had been sent...'
    if [ "$RUN_MODE" == "ONE_WAY" ]
    then
      sent=$(count_sent $APP_SIMULATOR_DB_NAMESPACE $dbPassword)
      received=$(count_received $APP_SIMULATOR_DB_NAMESPACE $dbPassword)
      echo "Sent [$sent] messages and received [$received] messages"

      if [[ "$sent" == "$received" ]]; then
        echo 'On to the next use case'
        stop="yes"
      fi
    else
      sent_a=$(count_sent $APP_SIMULATOR_DB_NAMESPACE_A $dbPasswordA)
      received_a=$(count_received $APP_SIMULATOR_DB_NAMESPACE_A $dbPasswordA)

      sent_b=$(count_sent $APP_SIMULATOR_DB_NAMESPACE_B $dbPasswordB)
      received_b=$(count_received $APP_SIMULATOR_DB_NAMESPACE_B $dbPasswordB)

      echo "Sent [$sent_a] messages and received [$received_b] messages on cluster A"
      echo "Sent [$sent_b] messages and received [$received_a] messages on cluster B"
      if [[ "$sent_a" == "$received_b" ]] && [[ "$sent_b" == "$received_a" ]]; then
        echo 'On to the next use case'
        stop="yes"
      fi
    fi
  done
  end=$(date -u '+%Y-%m-%d %H:%M:%S')
  if [ "$RUN_MODE" == "ONE_WAY" ]
  then
    latency=$(calculate_latency $APP_SIMULATOR_DB_NAMESPACE $dbPassword "$start" "$end")
    echo "Latency was $latency" >> "$reportFile"
  else
    latency_a=$(calculate_latency $APP_SIMULATOR_DB_NAMESPACE_A $dbPasswordA "$start" "$end")
    echo "Latency on cluster A was $latency_a" >> "$reportFile"
    latency_b=$(calculate_latency $APP_SIMULATOR_DB_NAMESPACE_B $dbPasswordB "$start" "$end")
    echo "Latency on cluster B was $latency_b" >> "$reportFile"
  fi
  if [ $1 == true ]
  then
    echo "This run was a warm up"
    # we don't want to stop the performance testing in case the warm up's latency was more than 1 sec
    latency=0
    latency_a=0
    latency_b=0
  fi
}

totalNumberOfMessages=200
interBatchDelay="PT1S"
batchSize=50
echo "Warm up"
run_sender true

totalNumberOfMessages=60000
interBatchDelay="PT0.3S"
batchSize=40
stop="no"
latency="0.22"
until (( $(echo "$latency > 1.0" |bc -l) || ($(echo "$latency_a > 1.0" |bc -l) || $(echo "$latency_b > 1.0" |bc -l)) ));  do
  echo 'Waiting a minute before starting sender'
  sleep 60
  run_sender false
  batchSize=$((batchSize + 10))
done

if [ "$RUN_MODE" == "ONE_WAY" ]
then
  echo "---" >> "$reportFile"
  write_report_file $APP_SIMULATOR_DB_NAMESPACE $dbPassword
else
  echo "---cluster A---" >> "$reportFile"
  write_report_file $APP_SIMULATOR_DB_NAMESPACE_A $dbPasswordA
  echo "---cluster B---" >> "$reportFile"
  write_report_file $APP_SIMULATOR_DB_NAMESPACE_B $dbPasswordB
fi


echo "Tearing down previous clusters"
"$SCRIPT_DIR"/tearDown.sh

echo "Report was saved into $reportFile"
