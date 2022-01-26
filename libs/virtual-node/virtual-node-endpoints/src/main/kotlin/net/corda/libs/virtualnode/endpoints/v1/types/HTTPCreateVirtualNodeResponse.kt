package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object received via HTTP in response to a request to create a virtual node.
 *
 * Exactly one of [cpiId] and [cpiIdHash] should be null.
 *
 * @param x500Name The X500 name for the new virtual node.
 * @param cpiId The long identifier of the CPI the virtual node is being created for.
 * @param cpiIdHash The short identifier of the CPI the virtual node is being created for.
 * @param mgmGroupId The identifier of the CPI's MGM.
 * @param holdingIdHash The holding identifier for the virtual node.
 */
data class HTTPCreateVirtualNodeResponse(
    val x500Name: String,
    val cpiId: CPIIdentifierHttp,
    val cpiIdHash: String?,
    val mgmGroupId: String,
    val holdingIdHash: String
    // TODO - Add optional crypto DB and vault DB connection strings.
)