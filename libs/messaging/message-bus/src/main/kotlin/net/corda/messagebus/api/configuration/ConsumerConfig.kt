package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ConsumerRoles

data class ConsumerConfig(
    val group: String,
    val clientId: String,
    val role: ConsumerRoles
)
