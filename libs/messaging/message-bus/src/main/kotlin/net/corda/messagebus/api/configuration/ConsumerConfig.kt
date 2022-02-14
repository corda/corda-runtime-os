package net.corda.messagebus.api.configuration

data class ConsumerConfig(
    val group: String,
    val clientId: String,
    // TODO: this isn't the correct way of specifying the topic prefix. It should be in the bus config as it is the
    // same for all consumers/producers in the process.
    val topicPrefix: String,
    val role: String
)
