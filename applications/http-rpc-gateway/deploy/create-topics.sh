#!/usr/bin/env bash

kafka_topics='/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'
create_kafka_topics="$kafka_topics --partitions 1 --replication-factor 1"

topic_prefix='http.rpc.permissions'

if [[ "$*" == "delete" ]]
  then
    echo "Delete all $topic_prefix topics"
    docker exec kafka bash -c "$kafka_topics --delete --topic \"$topic_prefix.*\""
fi

echo "(Re)create topics"
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.management"
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.management.resp"
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.user --config \"cleanup.policy=compact\""
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.group --config \"cleanup.policy=compact\""
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.role --config \"cleanup.policy=compact\""

echo "Topics Created:"
docker exec kafka bash -c "$kafka_topics --list"