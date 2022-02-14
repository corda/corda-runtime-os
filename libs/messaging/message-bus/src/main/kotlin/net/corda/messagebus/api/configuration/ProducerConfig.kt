package net.corda.messagebus.api.configuration

data class ProducerConfig(
    val clientId: String,
    val instanceId: Int?,
    // TODO: this isn't the correct way to specify topic prefix
    val topicPrefix: String,
    val role: String
)
