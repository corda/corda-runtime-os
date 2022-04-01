package net.corda.messagebus.api.configuration

import com.typesafe.config.Config

fun Config.getStringOrNull(path: String) = if (hasPath(path)) getString(path) else null
fun Config.getStringOrDefault(path: String, default: String): String = if (hasPath(path)) getString(path) else default

class ConfigProperties {
    companion object {
        const val TOPIC = "topic"
        const val TOPIC_NAME = "topic.name"

        const val GROUP = "group"
        const val GROUP_INSTANCE_ID = "group.instance.id"

        const val CLIENT_ID_COUNTER = "clientIdCounter"

        const val CORDA_PRODUCER = "producer"
        const val CORDA_CONSUMER = "consumer"

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

        const val TRANSACTIONAL_ID = "transactional.id"
        const val GROUP_ID = "group.id"
        const val CLIENT_ID = "client.id"
        const val PRODUCER_CLIENT_ID = "producer.$CLIENT_ID"
        const val CONSUMER_GROUP_ID = "consumer.group.id"
        const val CONSUMER_MAX_POLL_INTERVAL = "consumer.max.poll.interval.ms"
        const val PRODUCER_TRANSACTIONAL_ID = "producer.$TRANSACTIONAL_ID"
    }
}
