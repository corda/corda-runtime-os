#!/usr/bin/env bash

kafka_topics='docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'
create_kafka_topics="$kafka_topics --partitions 1 --replication-factor 1"

echo "Creating topics."
$create_kafka_topics --create --topic config-update-request --config "cleanup.policy=compact"

echo "Existing topics:"
$kafka_topics --list