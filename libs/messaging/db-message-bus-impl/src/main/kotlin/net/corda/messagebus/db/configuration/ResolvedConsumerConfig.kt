package net.corda.messagebus.db.configuration

import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy

/**
 * User configurable consumer values used as part of the consumer.
 * @param group Consumer group to join
 * @param clientId Client provided identifier for the client. Used for logging purposes.
 * @param maxPollSize Max amount of records to poll
 * @param offsetResetStrategy Strategy to use to set where in a topic to poll from when subscribing
 * @param jdbcUrl URL for database, set to null to use in-memory db
 * @param jdbcUser User for database
 * @param jdbcPass Password for database
 */
data class ResolvedConsumerConfig(
    val group: String,
    val clientId: String,
    val maxPollSize: Int,
    val offsetResetStrategy: CordaOffsetResetStrategy,
    val jdbcUrl: String?,
    val jdbcUser: String,
    val jdbcPass: String,
)
