#!/usr/bin/env bash

kafka_topics='docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'
create_kafka_topics="$kafka_topics --partitions 1 --replication-factor 1"

topic_prefix='rpc.permissions'

if [[ "$*" == "delete" ]]
  then
    echo "Delete all $topic_prefix topics"
    $kafka_topics --delete --topic "$topic_prefix.*"
fi

echo "(Re)create topics"
$create_kafka_topics --create --topic $topic_prefix.management
$create_kafka_topics --create --topic $topic_prefix.management.resp
$create_kafka_topics --create --topic $topic_prefix.user
$create_kafka_topics --create --topic $topic_prefix.group
$create_kafka_topics --create --topic $topic_prefix.role

echo "Topics Created:"
$kafka_topics --list