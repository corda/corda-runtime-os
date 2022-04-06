package net.corda.messagebus.db.configuration

/**
 * User configurable producer values as well as topic prefix.
 * @param clientId Client provided identifier for the client. Used for logging purposes.
 * @param jdbcUrl URL for database, set to null to use in-memory db
 * @param jdbcUser User for database
 * @param jdbcPass Password for database
 */
data class ResolvedProducerConfig(
    val clientId: String,
    val jdbcUrl: String?,
    val jdbcUser: String,
    val jdbcPass: String,
)
