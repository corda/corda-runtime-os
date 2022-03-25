package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ProducerRoles

/**
 * User configurable producer values as well as the role to extract from the messaging config
 * @param clientId Client provided identifier for the producer. Used for logging and producer message bus configuration.
 * @param instanceId Instance id for this producer.
 * @param role Producer role to extract config for from the message bus config
 */
data class ProducerConfig(
    val clientId: String,
    val instanceId: Int?,
    val role: ProducerRoles
)
