package net.corda.messagebus.kafka.config

data class ResolvedConsumerConfig(
    val group: String,
    val clientId: String,
    val topicPrefix: String
)
