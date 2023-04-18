package net.corda.e2etest.utilities.types

/**
 * Simple data class representing a notary service returned from a REST API call.
 */
data class Notary(
    val serviceName: String?,
    val protocolName: String?,
    val protocolVersions: List<String>,
    val keys: List<String>
)