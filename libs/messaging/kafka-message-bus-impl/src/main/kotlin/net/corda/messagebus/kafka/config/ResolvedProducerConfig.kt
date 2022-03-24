package net.corda.messagebus.kafka.config

/**
 * User configurable producer values as well as topic prefix.
 */
data class ResolvedProducerConfig(
    val clientId: String,
    val instanceId: Int?,
    val topicPrefix: String
)
