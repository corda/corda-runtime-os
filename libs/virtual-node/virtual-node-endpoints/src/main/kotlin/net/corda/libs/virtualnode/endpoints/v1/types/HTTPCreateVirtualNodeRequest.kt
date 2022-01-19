package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the creation of a virtual node.
 *
 * // TODO - Joel - Describe params.
 */
data class HTTPCreateVirtualNodeRequest(
    val x500Name: String,
    // TODO - Joel - See if I can use an embedded object here.
    val cpiName: String?,
    val cpiVersion: String?,
    val signerSummaryHash: String?,
    val cpiIdHash: String?
    // TODO - Joel - Optional crypto DB and Vault DB connection strings.
)