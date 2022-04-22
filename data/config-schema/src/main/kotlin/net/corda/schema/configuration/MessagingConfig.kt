package net.corda.schema.configuration

/**
 * Configuration keys to access public parts of the configuration under the corda.messaging key
 */
object MessagingConfig {

    /**
     * Configuration paths that should be merged into the messaging config block from the boot config.
     *
     * Note that these do not appear directly in the user-facing schema, as they are properties provided to the process
     * on start.
     */
    object Boot {
        const val INSTANCE_ID = "instance.id"
        const val TOPIC_PREFIX = "topic.prefix"
    }

    /**
     * Configuration for connecting to the underlying message bus.
     */
    object Bus {
        const val BUS = "bus"
        const val BUS_TYPE = "$BUS.busType"

        //consumer bus values
        const val MAX_POLL_RECORDS = "max.poll.records"
        const val AUTO_OFFSET_RESET = "auto.offset.reset"
        const val MAX_POLL_INTERVAL = "max.poll.interval.ms"
        const val CLIENT_ID = "client.id"

        //DB bus values
        const val JDBC_URL = "jdbc.url"
        const val JDBC_USER = "user"
        const val JDBC_PASS = "pass"

        //kafka values
        const val KAFKA_PROPERTIES = "$BUS.kafkaProperties"
        const val KAFKA_PROPERTIES_COMMON = "$KAFKA_PROPERTIES.common"
        const val BOOTSTRAP_SERVER = "$KAFKA_PROPERTIES_COMMON.bootstrap.servers"
        const val KAFKA_PROPERTIES_CONSUMER = "$KAFKA_PROPERTIES.consumer"
        const val KAFKA_PROPERTIES_PRODUCER = "$KAFKA_PROPERTIES.producer"
        const val KAFKA_CONSUMER_MAX_POLL_INTERVAL = "$KAFKA_PROPERTIES_CONSUMER.$MAX_POLL_INTERVAL"
        const val KAFKA_PRODUCER_CLIENT_ID = "$KAFKA_PROPERTIES_PRODUCER.$CLIENT_ID"


        //db values
        const val DB_PROPERTIES = "$BUS.dbProperties"
        const val DB_PROPERTIES_COMMON = "$DB_PROPERTIES.common"
        const val DB_JDBC_URL= "$DB_PROPERTIES_COMMON.$JDBC_URL"
        const val DB_USER = "$DB_PROPERTIES_COMMON.$JDBC_USER"
        const val DB_PASS = "$DB_PROPERTIES_COMMON.$JDBC_PASS"
        const val DB_PROPERTIES_CONSUMER = "$DB_PROPERTIES.consumer"
        const val DB_CONSUMER_MAX_POLL_RECORDS = "$DB_PROPERTIES_CONSUMER.$MAX_POLL_RECORDS"
        const val DB_CONSUMER_AUTO_OFFSET_RESET = "$DB_PROPERTIES_CONSUMER.$AUTO_OFFSET_RESET"
    }

    /**
     * Subscription related configuration.
     */
    object Subscription {
        const val SUBSCRIPTION = "subscription"
        const val POLL_TIMEOUT = "$SUBSCRIPTION.poll.timeout"
        const val THREAD_STOP_TIMEOUT = "$SUBSCRIPTION.thread.stop.timeout"
        const val PROCESSOR_RETRIES = "$SUBSCRIPTION.processor.retries"
        const val SUBSCRIBE_RETRIES = "$SUBSCRIPTION.subscribe.retries"
        const val COMMIT_RETRIES = "$SUBSCRIPTION.commit.retries"
        const val PROCESSOR_TIMEOUT = "$SUBSCRIPTION.processor.timeout"
    }

    /**
     * Publisher related configuration.
     */
    object Publisher {
        const val PUBLISHER = "publisher"
        const val CLOSE_TIMEOUT = "$PUBLISHER.close.timeout"
        const val TRANSACTIONAL = "$PUBLISHER.transactional"
    }
}