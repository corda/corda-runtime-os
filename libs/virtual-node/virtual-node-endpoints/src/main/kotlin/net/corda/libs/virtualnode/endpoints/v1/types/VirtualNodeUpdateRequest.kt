package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the update of a virtual node.
 *
 * @param cpiFileChecksum The checksum of the CPI file.
 */
data class VirtualNodeUpdateRequest(
    val cpiFileChecksum: String
)
