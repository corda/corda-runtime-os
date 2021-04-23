package net.corda.messaging.kafka.properties

class KafkaProperties {
    companion object {
        const val PRODUCER_CONF_PREFIX = "kafka.producer.props."
        const val PRODUCER_CLOSE_TIMEOUT = "kafka.producer.close.timeout"
        const val PRODUCER_CREATE_MAX_RETRIES = "kafka.subscription.producer.create.retries"

        const val CONSUMER_CONF_PREFIX = "kafka.consumer.props."
        const val CONSUMER_CREATE_MAX_RETRIES = "kafka.subscription.consumer.create.retries"
        const val CONSUMER_POLL_TIMEOUT = "kafka.subscription.consumer.poll.timeout"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "kafka.subscription.consumer.thread.stop.timeout"
    }
}