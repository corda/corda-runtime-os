package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ProducerRoles

/**
 * User configurable producer values as well as the role to extract from the messaging config
 */
data class ProducerConfig(
    val clientId: String,
    val instanceId: Int,
    val transactional: Boolean,
    val role: ProducerRoles
)
