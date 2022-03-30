package net.corda.messagebus.kafka.config

/**
 * User configurable producer values as well as topic prefix.
 * @param clientId Client provided identifier for the client. Used for logging purposes.
 * @param instanceId Instance id for this producer.
 * @param topicPrefix Topic prefix to apply to topic names before interacting with message bus.
 */
data class ResolvedProducerConfig(
    val clientId: String,
    val instanceId: Int?,
    val topicPrefix: String
)
