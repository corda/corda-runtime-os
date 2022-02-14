package net.corda.messagebus.api.configuration

data class ConsumerConfig(
    val group: String,
    val clientId: String,
    val topicPrefix: String,
    val role: String
)
