package net.corda.libs.virtualnode.endpoints.v1.types

/**
 * This class is serialized and returned as JSON in the REST API.
 *
 * These field names are what the end-users see.
 */
/**
 * List of virtual nodes
 *
 * @param virtualNodes List of [VirtualNodeInfo].
 */
data class VirtualNodes(
    val virtualNodes: List<VirtualNodeInfo>
)
