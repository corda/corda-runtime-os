package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ConsumerRoles

data class ConsumerConfig(
    val group: String,
    val clientId: String,
    // TODO: this isn't the correct way of specifying the topic prefix. It should be in the bus config as it is the
    // same for all consumers/producers in the process.
    val topicPrefix: String,
    val role: ConsumerRoles
)
