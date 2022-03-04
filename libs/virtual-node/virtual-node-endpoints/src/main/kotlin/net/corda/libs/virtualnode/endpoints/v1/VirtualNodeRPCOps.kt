package net.corda.libs.virtualnode.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeResponse
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPGetVirtualNodesResponse

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
        path = "create",
        title = "Create virtual node",
        description = "Creates a new virtual node.",
        responseDescription = "The details of the created virtual node."
    )
    fun createVirtualNode(
        @HttpRpcRequestBodyParameter(description = "Details of the virtual node to be created")
        request: HTTPCreateVirtualNodeRequest
    ): HTTPCreateVirtualNodeResponse

    /**
     * Lists all virtual nodes onboarded to the cluster.
     *
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    // TODO use proper/consistent resource naming (e.g. this should be mapped under /virtualnodes)
    @HttpRpcGET(
        path = "list",
        title = "List all virtual nodes in the cluster",
        description = "List all virtual nodes in the cluster.",
        responseDescription = "List details of the all virtual nodes in the cluster."
    )
    fun getAllVirtualNodes (): HTTPGetVirtualNodesResponse
}