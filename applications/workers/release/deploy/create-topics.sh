#!/usr/bin/env bash

kafka_command='docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'
kafka_flags="--partitions 1 --replication-factor 1 --create --topic"

echo "Creating topics."
$kafka_command $kafka_flags config-update-request --config "cleanup.policy=compact"
$kafka_command $kafka_flags config --config "cleanup.policy=compact"