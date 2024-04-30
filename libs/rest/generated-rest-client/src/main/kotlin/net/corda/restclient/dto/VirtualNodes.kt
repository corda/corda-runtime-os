package net.corda.restclient.dto

/**
 * This is a duplication of [net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodes]
 * Because we need to use a custom [VirtualNodeInfo]
 */
data class VirtualNodes(
    val virtualNodes: List<VirtualNodeInfo>
)
