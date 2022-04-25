package net.corda.messagebus.kafka.config

/**
 * User configurable consumer values used as part of the consumer.
 * @param group Consumer group to join
 * @param clientId Client provided identifier for the client. Used for logging purposes.
 * @param topicPrefix Topic prefix to apply to topic names before interacting with message bus.
 */
data class ResolvedConsumerConfig(
    val group: String,
    val clientId: String,
    val topicPrefix: String
)
