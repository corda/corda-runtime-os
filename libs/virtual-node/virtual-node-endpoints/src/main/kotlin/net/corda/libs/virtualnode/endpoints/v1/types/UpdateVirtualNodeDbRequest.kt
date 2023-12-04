package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object sent via HTTP to request the update of a virtual node.
 *
 * @param shortHash The short hash of the virtual node.
 */
data class UpdateVirtualNodeDbRequest(
    val vaultDdlConnection: String?,
    val vaultDmlConnection: String?,
    val cryptoDdlConnection: String?,
    val cryptoDmlConnection: String?,
    val uniquenessDdlConnection: String?,
    val uniquenessDmlConnection: String?
)