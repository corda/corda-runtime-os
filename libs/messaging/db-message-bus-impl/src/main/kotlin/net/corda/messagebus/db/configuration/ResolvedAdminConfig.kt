package net.corda.messagebus.db.configuration

data class ResolvedAdminConfig(
    val jdbcUrl: String?,
    val jdbcUser: String,
    val jdbcPass: String,
)
