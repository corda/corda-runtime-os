package net.corda.messagebus.kafka.config

/**
 * User configurable consumer values used as part of the consumer.
 */
data class ResolvedConsumerConfig(
    val group: String,
    val clientId: String,
    val topicPrefix: String
)
