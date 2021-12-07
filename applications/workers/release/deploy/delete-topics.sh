#!/usr/bin/env bash

kafka_command='docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'

echo "Deleting topics."
$kafka_command --delete --topic "*"