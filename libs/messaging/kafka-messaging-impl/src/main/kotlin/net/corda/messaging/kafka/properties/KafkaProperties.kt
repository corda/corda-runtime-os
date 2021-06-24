package net.corda.messaging.kafka.properties

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig

class KafkaProperties {
    companion object {
        const val TOPIC = "topic"
        const val GROUP = "group"
        const val INSTANCE_ID = "instanceId"
        const val CLIENT_ID_COUNTER = "clientIdCounter"

        const val TOPIC_PREFIX = "topic.prefix"
        const val TOPIC_NAME = "topic.name"

        const val KAFKA_PRODUCER = "producer"
        const val KAFKA_CONSUMER = "consumer"

        const val PATTERN_PUBLISHER = "messaging.pattern.publisher"
        const val PATTERN_PUBSUB = "messaging.pattern.pubsub"
        const val PATTERN_DURABLE = "messaging.pattern.durable"
        const val PATTERN_COMPACTED = "messaging.pattern.compacted"
        const val PATTERN_STATEANDEVENT = "messaging.pattern.stateAndEvent"
        const val PATTERN_EVENTLOG = "messaging.pattern.eventLog"
        const val PATTERN_RANDOMACCESS = "messaging.pattern.randomAccess"

        const val CLOSE_TIMEOUT = "close.timeout"
        const val PRODUCER_CLOSE_TIMEOUT = "producer.$CLOSE_TIMEOUT"
        const val CONSUMER_CLOSE_TIMEOUT = "consumer.$CLOSE_TIMEOUT"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "consumer.thread.stop.timeout"
        const val CONSUMER_POLL_AND_PROCESS_RETRIES = "consumer.processor.retries"
        const val POLL_TIMEOUT = "poll.timeout"
        const val CONSUMER_POLL_TIMEOUT = "consumer.poll.timeout"
        const val CONSUMER_SUBSCRIBE_MAX_RETRIES = "consumer.subscribe.retries"
        const val CONSUMER_COMMIT_OFFSET_MAX_RETRIES = "consumer.commit.retries"
        const val SUBSCRIBE_MAX_RETRIES = "subscribe.retries"
        const val COMMIT_OFFSET_MAX_RETRIES = "commit.retries"

        const val GROUP_INSTANCE_ID = CommonClientConfigs.GROUP_INSTANCE_ID_CONFIG
        const val PRODUCER_CLIENT_ID = "producer.${CommonClientConfigs.CLIENT_ID_CONFIG}"
        const val CONSUMER_GROUP_ID = "consumer.${CommonClientConfigs.GROUP_ID_CONFIG}"
        const val PRODUCER_TRANSACTIONAL_ID = "producer.${ProducerConfig.TRANSACTIONAL_ID_CONFIG}"
    }
}
