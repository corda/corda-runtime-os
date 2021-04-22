package net.corda.messaging.kafka.properties

class KafkaProperties {
    companion object {
        const val KAFKA_CLOSE_TIMEOUT = "kafka.producer.close.timeout"
        const val PRODUCER_CONF_PREFIX = "kafka.producer.props."
        const val CONSUMER_CONF_PREFIX = "kafka.consumer.props."
        const val MAX_RETIES_CONFIG = "kafka.subscription.consumer.create.retries"
    }
}