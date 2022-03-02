package net.corda.messagebus.kafka.config

data class ResolvedProducerConfig(
    val clientId: String,
    val instanceId: Int?,
    val topicPrefix: String
)
