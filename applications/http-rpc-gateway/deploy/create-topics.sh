#!/usr/bin/env bash

kafka_topics='/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092'
create_kafka_topics="$kafka_topics --partitions 1 --replication-factor 1"

topic_prefix='http.'

if [[ "$*" == "delete" ]]
  then
    echo "Delete all $topic_prefix topics"
    docker exec kafka bash -c "$kafka_topics --delete --topic \"$topic_prefix.*\""
fi

echo "(Re)create topics"
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.ConfigTopic --config \"cleanup.policy=compact\""
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.rpc.permissions.management"
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.rpc.permissions.management.resp"
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.rpc.permissions.user --config \"cleanup.policy=compact\""
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.rpc.permissions.group --config \"cleanup.policy=compact\""
docker exec kafka bash -c "$create_kafka_topics --create --topic $topic_prefix.rpc.permissions.role --config \"cleanup.policy=compact\""

echo "Topics Created:"
docker exec kafka bash -c "$kafka_topics --list"