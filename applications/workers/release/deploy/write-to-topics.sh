#!/usr/bin/env bash

kafka_script='/opt/bitnami/kafka/bin/kafka-console-producer.sh'
kafka_config='--topic config-update-request --bootstrap-server localhost:9092 --property "parse.key=true" --property "key.separator=:"'
timestamp=$(date +%T)

echo "Writing to topics."
docker exec kafka bash -c "$kafka_script $kafka_config <<< \"timestamp=$timestamp\""