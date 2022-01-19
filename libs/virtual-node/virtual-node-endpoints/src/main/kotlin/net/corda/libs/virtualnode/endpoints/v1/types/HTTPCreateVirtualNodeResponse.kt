package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object received via HTTP in response to a request to update cluster configuration.
 *
 * // TODO - Joel - Describe params.
 */
data class HTTPCreateVirtualNodeResponse(
    val x500Name: String,
    // TODO - Joel - See if I can use an embedded object here.
    val cpiName: String?,
    val cpiVersion: String?,
    val signerSummaryHash: String?,
    val cpiIdHash: String?
    // TODO - Joel - Optional crypto DB and Vault DB connection strings.
)