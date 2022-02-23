package net.corda.introspiciere.core

interface KafkaConfig {
    /**
     * Returns the kafka brokers.
     */
    val brokers: String
}

class KafkaConfigImpl(private val overrideBrokers: String? = null) : KafkaConfig {
    override val brokers: String
        get() = overrideBrokers ?: System.getenv("KAFKA_BROKERS")
}