package net.corda.libs.virtualnode.maintenance.endpoints.v1.types

/**
 * The data object received via HTTP in response to a request to update the state of a virtual node.
 *
 * @param holdingIdShortHash The short ID for the virtual node to have it's state updated.
 * @param flowP2pOperationalStatus The new flowP2pOperationalStatus for the virtual node.
 * @param flowStartOperationalStatus The new flowStartOperationalStatus for the virtual node.
 * @param flowOperationalStatus The new flowOperationalStatus for the virtual node.
 * @param vaultDbOperationalStatus The new vaultDbOperationalStatus for the virtual node.
 */
data class ChangeVirtualNodeStateResponse(
    val holdingIdShortHash: String,
    val flowP2pOperationalStatus: String,
    val flowStartOperationalStatus: String,
    val flowOperationalStatus: String,
    val vaultDbOperationalStatus: String,
)
