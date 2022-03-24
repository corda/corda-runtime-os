package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ProducerRoles

data class ProducerConfig(
    val clientId: String,
    val instanceId: Int,
    val transactional: Boolean,
    val role: ProducerRoles
)
