package net.corda.e2etest.utilities.types

/**
 * Simple data class representing a notary service returned from a REST API call.
 *
 * @param serviceName the notary service name
 * @param protocolName the notary service protocol name
 * @param protocolVersions the list of supported protocol versions
 * @param keys the list of notary keys in PEM format for this notary service.
 */
data class Notary(
    val serviceName: String?,
    val protocolName: String?,
    val protocolVersions: List<String>,
    val keys: List<String>
)