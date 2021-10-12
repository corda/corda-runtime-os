#!/usr/bin/env bash

kafka_topics='docker exec kafka /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'
topic_prefix='persistence-demo'

if [[ "$*" == "delete" ]]
  then
    echo "Delete all $topic_prefix topics"
    $kafka_topics --delete --topic "$topic_prefix.*"
fi

echo "(Re)create topics"
$kafka_topics --create --topic $topic_prefix-cluster-admin-event
$kafka_topics --create --topic $topic_prefix-config-event
$kafka_topics --create --topic $topic_prefix-config-state --config "cleanup.policy=compact"
$kafka_topics --create --topic $topic_prefix-db-event
$kafka_topics --create --topic $topic_prefix-db-state --config "cleanup.policy=compact"

echo "Topics Created:"
$kafka_topics --list