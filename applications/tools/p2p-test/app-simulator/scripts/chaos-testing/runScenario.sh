#!/bin/bash

# Run a simple chaos testing scenario
# Deploy a cluster with a given 2 instances of the P2P layer start sending messages in fixed rate and
# injecting pod-failure to the gateways and link managers for a few seconds and make sure that all the messages
# had passed through.
#
# To run use:
# DOCKER_IMAGE_VERSION=<IMAGE_TAG>.  ./applications/tools/p2p-test/app-simulator/scripts/chaos-testing/runScenario.sh

set -e
TESTING_DIR=$(dirname ${BASH_SOURCE[0]})
SCRIPT_DIR="$TESTING_DIR/.."


reportDir="$SCRIPT_DIR/build/reports"
mkdir -p $reportDir
reportFile="$SCRIPT_DIR/build/reports/report-chaos.txt"
echo "Report in $reportFile"
echo "" > "$reportFile"

echo "Running scenario: \"$(echo "$scenario" | jq -r '.name')\""
export KAFKA_REPLICAS=3
export WORKER_REPLICAS=2
export KAFKA_REPLICATION_FACTOR=3
export KAFKA_PARTITION_COUNT=10
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

export CORDA_EKS_FILE="$SCRIPT_DIR/corda-eks-small.yaml"
export PREREQS_EKS_FILE="$SCRIPT_DIR/prereqs-eks-small.yaml"
source "$SCRIPT_DIR/settings.sh"

RUN_MODE="ONE_WAY"
echo "Tearing down previous clusters"
"$SCRIPT_DIR"/tearDown.sh
echo "Deploying clusters"
"$SCRIPT_DIR"/deploy.sh
echo "Onboarding clusters"
"$SCRIPT_DIR"/onBoardCluster.sh
echo "Deploying receiver in $RUN_MODE mode"
"$SCRIPT_DIR"/runReceiver.sh "$RUN_MODE"
dbPassword=$(kubectl get secret --namespace $APP_SIMULATOR_DB_NAMESPACE db-postgresql -o jsonpath="{.data.postgres-password}" | base64 -d)

count_sent() {
  echo $(kubectl exec -n $1 db-postgresql-0 -- env PGPASSWORD=$2 psql -U postgres -d app_simulator -c "select to_char(count(*), 'FM999,999,999') from sent_messages;" -t 2>/dev/null | xargs)
}

count_received() {
  echo $(kubectl exec -n $1 db-postgresql-0 -- env PGPASSWORD=$2 psql -U postgres -d app_simulator -c "select to_char(count(*), 'FM999,999,999') from received_messages;" -t  2>/dev/null | xargs)
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

my_sleep() {
  duration=$1
  if command -v pv &> /dev/null
  then
    yes | pv -SpeL10 -i0.1 -s "$((10*duration))" > /dev/null
  else
    sleep $duration
  fi
}

wait_for_all_messages() {
  echo "Waiting for messages"
  stop="no"
  until [[ "$stop" == "yes" ]];  do
    echo 'Waiting...'
    my_sleep 1
    echo 'Checking how many messages had been sent...'
    sent=$(count_sent $APP_SIMULATOR_DB_NAMESPACE $dbPassword)
    received=$(count_received $APP_SIMULATOR_DB_NAMESPACE $dbPassword)
    echo "Sent [$sent] messages and received [$received] messages"

    if [[ "$sent" == "$received" ]]; then
      echo 'All messages had been received'
      stop="yes"
    fi
  done
}
echo "Warming up..."
echo "---" >> "$reportFile"
start=$(date -u '+%Y-%m-%d %H:%M:%S')
totalNumberOfMessages=200
interBatchDelay="PT1S"
batchSize=50
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
echo "Running sender..."
SENDER_DETAILS_FILE=$senderDetailsFile "$SCRIPT_DIR"/runSender.sh "$RUN_MODE"
rm -rf senderDetailsFile
wait_for_all_messages

echo "Running sender..."
echo "---" >> "$reportFile"
start=$(date -u '+%Y-%m-%d %H:%M:%S')
interBatchDelay="PT0.1S"
batchSize=5
echo $start >> "$reportFile"
echo "interBatchDelay: $interBatchDelay" >> "$reportFile"
echo "batchSize: $batchSize" >> "$reportFile"
export senderDetailsFile=$(mktemp)
echo '{}' \
  | jq '.appSimulators.sender.loadGenerationType="CONTINUOUS"' \
  | jq ".appSimulators.sender.interBatchDelay=\"$interBatchDelay\"" \
  | jq ".appSimulators.sender.batchSize=$batchSize" \
  | jq '.db.appSimulator.user="postgres"' \
  | jq '.appSimulators.sender.enabled=true' \
  > $senderDetailsFile
echo "Running sender..."
SENDER_DETAILS_FILE=$senderDetailsFile "$SCRIPT_DIR"/runSender.sh "$RUN_MODE" &

report_and_sleep() {
  my_sleep 60
  received=$(count_received $APP_SIMULATOR_DB_NAMESPACE $dbPassword)
  sent=$(count_sent $APP_SIMULATOR_DB_NAMESPACE $dbPassword)
  now=$(date -u '+%Y-%m-%d %H:%M:%S')
  echo "On $now sent $sent messages and received $received messages" >> "$reportFile"
  echo "Sent [$sent] messages and received [$received] messages"
}

introduce_failure() {
  echo "Introducing failure to $1 $2 1..."
  kubectl delete pod -n $1 $(kubectl get pods -n $1 | awk '{print $1}' | grep corda-p2p-$2-worker | shuf -n 1)
  now=$(date -u '+%Y-%m-%d %H:%M:%S')
  echo "On $now killed $2 on $1" >> "$reportFile"
  report_and_sleep
}

echo "Sender is running..."
for i in {1..20}; do
  echo "Iteration $i, waiting a minute..."
  report_and_sleep

  introduce_failure $A_CLUSTER_NAMESPACE "link-manager"
  introduce_failure $B_CLUSTER_NAMESPACE "link-manager"
  introduce_failure $A_CLUSTER_NAMESPACE "gateway"
  introduce_failure $B_CLUSTER_NAMESPACE "gateway"
done
echo "Stopping sender"
helm uninstall -n $A_CLUSTER_NAMESPACE app-simulator-sender

rm -rf senderDetailsFile

wait_for_all_messages
echo "---" >> "$reportFile"
write_report_file $APP_SIMULATOR_DB_NAMESPACE $dbPassword

echo "Tearing down previous clusters"
"$SCRIPT_DIR"/tearDown.sh

echo "Report was saved into $reportFile"
