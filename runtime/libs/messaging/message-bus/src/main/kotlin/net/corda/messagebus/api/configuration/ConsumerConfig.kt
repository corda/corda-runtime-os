package net.corda.messagebus.api.configuration

import net.corda.messagebus.api.constants.ConsumerRoles

/**
 * User configurable consumer values as well as the role to extract from the messaging config
 * @param group Consumer group to join
 * @param clientId Client provided identifier for the consumer. Used for logging purposes.
 * @param role Consumer role to extract config for from the message bus config
 */
data class ConsumerConfig(
    val group: String,
    val clientId: String,
    val role: ConsumerRoles
)
