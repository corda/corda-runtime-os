package net.corda.messagebus.api.configuration

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

        const val GROUP_ID = "consumer.id"
        const val GROUP_INSTANCE_ID = "group.instance.id"
        const val PRODUCER_CLIENT_ID = "producer.client.id"
        const val CONSUMER_GROUP_ID = "consumer.group.id"
        const val CONSUMER_MAX_POLL_INTERVAL = "consumer.max.poll.interval.ms"
        const val PRODUCER_TRANSACTIONAL_ID = "producer.transactional.id"

        const val STATE_TOPIC_NAME = "$STATE_CONSUMER.$TOPIC_NAME"

        const val EVENT_GROUP_ID = "$EVENT_CONSUMER.$CONSUMER_GROUP_ID"
        val EVENT_CONSUMER_THREAD_STOP_TIMEOUT = CONSUMER_THREAD_STOP_TIMEOUT.replace("consumer", "eventConsumer")
        val EVENT_CONSUMER_POLL_AND_PROCESS_RETRIES = CONSUMER_POLL_AND_PROCESS_RETRIES.replace("consumer", "eventConsumer")
        val EVENT_CONSUMER_CLOSE_TIMEOUT = CONSUMER_CLOSE_TIMEOUT.replace("consumer", "eventConsumer")
    }
}
