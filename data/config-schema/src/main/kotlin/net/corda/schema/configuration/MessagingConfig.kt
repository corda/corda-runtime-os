package net.corda.schema.configuration

/**
 * Configuration keys to access public parts of the configuration under the corda.messaging key
 */
object MessagingConfig {

    /**
     * Configuration for connecting to the underlying message bus.
     */
    object Bus {
        const val BUS = "bus"
        const val BUS_TYPE = "$BUS.busType"

        const val AUTO_OFFSET_RESET = "auto.offset.reset"

        // kafka values
        const val KAFKA_PROPERTIES = "$BUS.kafkaProperties"
        const val KAFKA_PROPERTIES_COMMON = "$KAFKA_PROPERTIES.common"
        const val KAFKA_BOOTSTRAP_SERVERS = "$KAFKA_PROPERTIES_COMMON.bootstrap.servers"
        const val KAFKA_PROPERTIES_CONSUMER = "$KAFKA_PROPERTIES.consumer"
        const val KAFKA_CONSUMER_MAX_POLL_INTERVAL = "$KAFKA_PROPERTIES_CONSUMER.max.poll.interval.ms"
        const val KAFKA_PROPERTIES_PRODUCER = "$KAFKA_PROPERTIES.producer"
        const val KAFKA_PRODUCER_CLIENT_ID = "$KAFKA_PROPERTIES_PRODUCER.client.id"

        // db values
        const val JDBC_URL = "jdbcUrl"
        const val JDBC_USER = "user"
        const val JDBC_PASS = "pass"
        const val DB_MAX_POLL_RECORDS = "maxPollRecords"

        const val DB_PROPERTIES = "$BUS.dbProperties"
        const val DB_JDBC_URL = "$DB_PROPERTIES.$JDBC_URL"
        const val DB_USER = "$DB_PROPERTIES.$JDBC_USER"
        const val DB_PASS = "$DB_PROPERTIES.$JDBC_PASS"
        const val DB_PROPERTIES_CONSUMER = "$DB_PROPERTIES.consumer"
        const val DB_CONSUMER_MAX_POLL_RECORDS = "$DB_PROPERTIES_CONSUMER.$DB_MAX_POLL_RECORDS"
        const val DB_CONSUMER_AUTO_OFFSET_RESET = "$DB_PROPERTIES_CONSUMER.$AUTO_OFFSET_RESET"
    }

    /**
     * Subscription related configuration.
     */
    object Subscription {
        const val SUBSCRIPTION = "subscription"
        const val POLL_TIMEOUT = "$SUBSCRIPTION.pollTimeout"
        const val THREAD_STOP_TIMEOUT = "$SUBSCRIPTION.threadStopTimeout"
        const val PROCESSOR_RETRIES = "$SUBSCRIPTION.processorRetries"
        const val SUBSCRIBE_RETRIES = "$SUBSCRIPTION.subscribeRetries"
        const val COMMIT_RETRIES = "$SUBSCRIPTION.commitRetries"
        const val PROCESSOR_TIMEOUT = "$SUBSCRIPTION.processorTimeout"
    }

    /**
     * Publisher related configuration.
     */
    object Publisher {
        const val PUBLISHER = "publisher"
        const val CLOSE_TIMEOUT = "$PUBLISHER.closeTimeout"
        const val TRANSACTIONAL = "$PUBLISHER.transactional"
    }

    /**
     * Maximum Allowed Message Size (in Bytes)
     * NOTE: this is not sync'ed with the actual Kafka configuration and is just a guide for
     * producers to keep under this limit when publishing messages.
     */
    const val MAX_ALLOWED_MSG_SIZE = "maxAllowedMessageSize"
}
