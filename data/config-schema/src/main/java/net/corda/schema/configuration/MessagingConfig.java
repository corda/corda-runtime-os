package net.corda.schema.configuration;

/**
 * Configuration keys to access public parts of the configuration under the {@code corda.messaging} key
 */
public final class MessagingConfig {
    private MessagingConfig() {
    }

    /**
     * Configuration for connecting to the underlying message bus.
     */
    public static final class Bus {
        private Bus() {
        }

        public static final String BUS = "bus";
        public static final String BUS_TYPE = BUS + ".busType";

        public static final String AUTO_OFFSET_RESET = "auto.offset.reset";

        // kafka values
        public static final String KAFKA_PROPERTIES = BUS + ".kafkaProperties";
        public static final String KAFKA_PROPERTIES_COMMON = KAFKA_PROPERTIES + ".common";
        public static final String KAFKA_BOOTSTRAP_SERVERS = KAFKA_PROPERTIES_COMMON + ".bootstrap.servers";
        public static final String KAFKA_PROPERTIES_CONSUMER = KAFKA_PROPERTIES + ".consumer";
        public static final String KAFKA_CONSUMER_MAX_POLL_INTERVAL = KAFKA_PROPERTIES_CONSUMER + ".max.poll.interval.ms";
        public static final String KAFKA_PROPERTIES_PRODUCER = KAFKA_PROPERTIES + ".producer";
        public static final String KAFKA_PRODUCER_CLIENT_ID = KAFKA_PROPERTIES_PRODUCER + ".client.id";

        // db values
        public static final String JDBC_URL = "jdbcUrl";
        public static final String JDBC_USER = "user";
        public static final String JDBC_PASS = "pass";
        public static final String DB_MAX_POLL_RECORDS = "maxPollRecords";

        public static final String DB_PROPERTIES = BUS + ".dbProperties";
        public static final String DB_JDBC_URL = DB_PROPERTIES + '.' + JDBC_URL;
        public static final String DB_USER = DB_PROPERTIES + '.' + JDBC_USER;
        public static final String DB_PASS = DB_PROPERTIES + '.' + JDBC_PASS;
        public static final String DB_PROPERTIES_CONSUMER = DB_PROPERTIES + ".consumer";
        public static final String DB_CONSUMER_MAX_POLL_RECORDS = DB_PROPERTIES_CONSUMER + '.' + DB_MAX_POLL_RECORDS;
        public static final String DB_CONSUMER_AUTO_OFFSET_RESET = DB_PROPERTIES_CONSUMER + '.' + AUTO_OFFSET_RESET;
    }

    /**
     * Subscription related configuration.
     */
    public static final class Subscription {
        private Subscription() {
        }

        public static final String SUBSCRIPTION = "subscription";
        public static final String POLL_TIMEOUT = SUBSCRIPTION + ".pollTimeout";
        public static final String THREAD_STOP_TIMEOUT = SUBSCRIPTION + ".threadStopTimeout";
        public static final String PROCESSOR_RETRIES = SUBSCRIPTION + ".processorRetries";
        public static final String SUBSCRIBE_RETRIES = SUBSCRIPTION + ".subscribeRetries";
        public static final String COMMIT_RETRIES = SUBSCRIPTION + ".commitRetries";
        public static final String PROCESSOR_TIMEOUT = SUBSCRIPTION + ".processorTimeout";
    }

    /**
     * Publisher related configuration.
     */
    public static final class Publisher {
        private Publisher() {
        }

        public static final String PUBLISHER = "publisher";
        public static final String CLOSE_TIMEOUT = PUBLISHER + ".closeTimeout";
        public static final String TRANSACTIONAL = PUBLISHER + ".transactional";
    }

    /**
     * Maximum Allowed Message Size (in Bytes)
     * NOTE: this is not sync'ed with the actual Kafka configuration and is just a guide for
     * producers to keep under this limit when publishing messages.
     */
    public static final String MAX_ALLOWED_MSG_SIZE = "maxAllowedMessageSize";
}
