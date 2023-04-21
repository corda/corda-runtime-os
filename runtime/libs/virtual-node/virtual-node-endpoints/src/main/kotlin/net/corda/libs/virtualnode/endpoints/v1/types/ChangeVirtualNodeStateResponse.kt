package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * The data object received via HTTP in response to a request to update the state of a virtual node.
 *
 * @param holdingIdShortHash The short ID for the virtual node to have it's state updated.
 * @param newState The desired new state for the virtual node.
 */
data class ChangeVirtualNodeStateResponse(
    val holdingIdShortHash: String,
    val newState: String
)
