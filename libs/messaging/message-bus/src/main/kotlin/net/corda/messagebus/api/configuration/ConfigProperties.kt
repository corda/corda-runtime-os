package net.corda.messagebus.api.configuration

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig

class ConfigProperties {
    companion object {
        const val TOPIC = "topic"
        const val DEAD_LETTER_QUEUE_SUFFIX = "topic.deadLetterQueueSuffix"
        const val GROUP = "group"
        const val INSTANCE_ID = "instanceId"
        const val CLIENT_ID_COUNTER = "clientIdCounter"

        const val TOPIC_PREFIX = "topic.prefix"
        const val TOPIC_NAME = "topic.name"
        const val RESPONSE_TOPIC = "responseTopic"

        const val KAFKA_PRODUCER = "producer"
        const val KAFKA_CONSUMER = "consumer"
        const val STATE_CONSUMER = "stateConsumer"
        const val EVENT_CONSUMER = "eventConsumer"

        const val MESSAGING_KAFKA = "messaging.kafka"

        const val PATTERN_PUBLISHER = "messaging.pattern.publisher"
        const val PATTERN_PUBSUB = "messaging.pattern.pubsub"
        const val PATTERN_DURABLE = "messaging.pattern.durable"
        const val PATTERN_COMPACTED = "messaging.pattern.compacted"
        const val PATTERN_STATEANDEVENT = "messaging.pattern.stateAndEvent"
        const val PATTERN_EVENTLOG = "messaging.pattern.eventLog"
        const val PATTERN_RANDOMACCESS = "messaging.pattern.randomAccess"
        const val PATTERN_RPC_SENDER = "messaging.pattern.rpcSender"
        const val PATTERN_RPC_RESPONDER = "messaging.pattern.rpcResponder"

        const val CLOSE_TIMEOUT = "close.timeout"
        const val PRODUCER_CLOSE_TIMEOUT = "producer.$CLOSE_TIMEOUT"
        const val CONSUMER_CLOSE_TIMEOUT = "consumer.$CLOSE_TIMEOUT"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "consumer.thread.stop.timeout"
        const val CONSUMER_PROCESSOR_TIMEOUT = "consumer.processor.timeout"
        const val CONSUMER_POLL_AND_PROCESS_RETRIES = "consumer.processor.retries"
        const val POLL_TIMEOUT = "poll.timeout"
        const val CONSUMER_POLL_TIMEOUT = "consumer.poll.timeout"
        const val CONSUMER_SUBSCRIBE_MAX_RETRIES = "consumer.subscribe.retries"
        const val CONSUMER_COMMIT_OFFSET_MAX_RETRIES = "consumer.commit.retries"
        const val SUBSCRIBE_MAX_RETRIES = "subscribe.retries"
        const val COMMIT_OFFSET_MAX_RETRIES = "commit.retries"

        const val GROUP_INSTANCE_ID = org.apache.kafka.clients.CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG
        const val PRODUCER_CLIENT_ID = "producer.${org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG}"
        const val CONSUMER_GROUP_ID = "consumer.${org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG}"
        const val CONSUMER_MAX_POLL_INTERVAL = "consumer.${org.apache.kafka.clients.CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG}"
        const val PRODUCER_TRANSACTIONAL_ID = "producer.${org.apache.kafka.clients.producer.ProducerConfig.TRANSACTIONAL_ID_CONFIG}"

        const val STATE_TOPIC_NAME = "$STATE_CONSUMER.$TOPIC_NAME"

        const val EVENT_GROUP_ID = "$EVENT_CONSUMER.${org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG}"
        val EVENT_CONSUMER_THREAD_STOP_TIMEOUT = CONSUMER_THREAD_STOP_TIMEOUT.replace("consumer", "eventConsumer")
        val EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES = CONSUMER_POLL_AND_PROCESS_RETRIES.replace("consumer", "eventConsumer")
        val EVENT_CONSUMER_CLOSE_TIMEOUT = CONSUMER_CLOSE_TIMEOUT.replace("consumer", "eventConsumer")
    }
}
