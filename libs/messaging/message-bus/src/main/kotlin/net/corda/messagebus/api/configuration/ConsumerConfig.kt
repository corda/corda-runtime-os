package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ConsumerRoles

/**
 * User configurable consumer values as well as the role to extract from the messaging config
 */
data class ConsumerConfig(
    val group: String,
    val clientId: String,
    val role: ConsumerRoles
)
