package net.corda.e2etest.utilities.externaldb

data class ExternalDBConnectionParams(
    val cryptoDdlConnection: String? = null,
    val cryptoDmlConnection: String? = null,
    val uniquenessDdlConnection: String? = null,
    val uniquenessDmlConnection: String? = null,
    val vaultDdlConnection: String? = null,
    val vaultDmlConnection: String? = null
)