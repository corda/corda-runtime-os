package net.corda.libs.virtualnode.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeParameters
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeResponse
import net.corda.libs.virtualnode.endpoints.v1.types.GetVirtualNodesResponse
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPVirtualNodeStateChangeResponse

/** RPC operations for virtual node management. */
@HttpRpcResource(
    name = "VirtualNodeRPCOps",
    description = "Virtual node management endpoints",
    path = "virtualnode"
)
interface VirtualNodeRPCOps : RpcOps {

    /**
     * Creates a virtual node.
     *
     * @throws `VirtualNodeRPCOpsServiceException` If the virtual node creation request could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPOST(
        title = "Create virtual node",
        description = "Creates a new virtual node.",
        responseDescription = "The details of the created virtual node."
    )
    fun createVirtualNode(
        @HttpRpcRequestBodyParameter(description = "Details of the virtual node to be created")
        request: CreateVirtualNodeParameters
    ): CreateVirtualNodeResponse

    /**
     * Lists all virtual nodes onboarded to the cluster.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcGET(
        title = "List all virtual nodes in the cluster",
        description = "List all virtual nodes in the cluster.",
        responseDescription = "List details of the all virtual nodes in the cluster."
    )
    fun getAllVirtualNodes(): GetVirtualNodesResponse

    /**
     * Updates a virtual nodes state.
     *
     * @throws `VirtualNodeRPCOpsServiceException` If the virtual node update request could not be published.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPUT(
        path = "{virtualNodeShortId}",
        title = "Update virtual node state",
        description = "Updates the state of a new virtual node.",
        responseDescription = "The details of the updated virtual node."
    )
    fun updateVirtualNodeState(
        @HttpRpcPathParameter(description = "Short ID of the virtual node instance to update")
        virtualNodeShortId: String,
        @HttpRpcRequestBodyParameter(description = "Details of the virtual node to be created")
        newState: String
    ): HTTPVirtualNodeStateChangeResponse
}
