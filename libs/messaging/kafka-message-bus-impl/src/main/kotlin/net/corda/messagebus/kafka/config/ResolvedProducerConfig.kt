package net.corda.messagebus.kafka.config

data class ResolvedProducerConfig(
    val clientId: String,
    val transactional: Boolean,
    val topicPrefix: String
)
