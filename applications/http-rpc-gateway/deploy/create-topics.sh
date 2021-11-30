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
$create_kafka_topics --create --topic $topic_prefix.user --config "cleanup.policy=compact"
$create_kafka_topics --create --topic $topic_prefix.group --config "cleanup.policy=compact"
$create_kafka_topics --create --topic $topic_prefix.role --config "cleanup.policy=compact"

echo "Topics Created:"
$kafka_topics --list