package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the creation of a virtual node.
 *
 * @param x500Name The X500 name for the new virtual node.
 * @param cpiFileChecksum The checksum of the CPI file.
 */
data class CreateVirtualNodeRequest(
    val x500Name: String,
    val cpiFileChecksum: String,
    val vaultDdlConnection: String?,
    val vaultDmlConnection: String?,
    val cryptoDdlConnection: String?,
    val cryptoDmlConnection: String?,
    val uniquenessDdlConnection: String?,
    val uniquenessDmlConnection: String?
)
