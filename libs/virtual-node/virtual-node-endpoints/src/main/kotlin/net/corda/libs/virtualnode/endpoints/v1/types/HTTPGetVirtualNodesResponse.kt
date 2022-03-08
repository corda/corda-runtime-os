package net.corda.libs.virtualnode.endpoints.v1.types

import net.corda.virtualnode.VirtualNodeInfo


/**
 * The data object received via HTTP in response to a request to get virtual nodes in the cluster.
 *
 * @param virtualNodes List of virtual nodes.
 */
data class HTTPGetVirtualNodesResponse(
    val virtualNodes: List<VirtualNodeInfo>
)