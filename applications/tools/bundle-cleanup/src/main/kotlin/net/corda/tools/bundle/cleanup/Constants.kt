package net.corda.tools.bundle.cleanup

internal const val KAFKA_BOOTSTRAP_SERVERS_KEY = "messaging.kafka.common.bootstrap.servers"
internal const val KAFKA_BOOTSTRAP_SERVERS = "localhost:9093"
internal const val KAFKA_TOPIC_PREFIX_KEY = "messaging.topic.prefix"
internal const val KAFKA_TOPIC_PREFIX = "bundle-cleanup-"
internal const val KAFKA_TOPIC_SUFFIX = "bundle-cleanup-topic"
internal const val KAFKA_TOPIC = "$KAFKA_TOPIC_PREFIX$KAFKA_TOPIC_SUFFIX"
internal const val KAFKA_CLIENT_ID = "bundle-cleanup-client"
internal const val KAFKA_GROUP_NAME = "joel-group"