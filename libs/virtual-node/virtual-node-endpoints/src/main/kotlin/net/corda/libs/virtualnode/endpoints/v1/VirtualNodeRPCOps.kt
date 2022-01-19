package net.corda.libs.virtualnode.endpoints.v1

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeRequest
import net.corda.libs.virtualnode.endpoints.v1.types.HTTPCreateVirtualNodeResponse

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
     * // TODO - Joel - Work out what exceptions are thrown.
     * @throws `VirtualNodeRPCOpsServiceException` If the virtual node could not be created.
     * @throws `HttpApiException` If the request returns an exceptional response.
     */
    @HttpRpcPOST(
        path = "create",
        title = "Create virtual node",
        description = "Creates a new virtual node.",
        responseDescription = "The details of the created virtual node."
    )
    fun createVirtualNode(
        @HttpRpcRequestBodyParameter(description = "Details of the virtual node to be created", required = true)
        request: HTTPCreateVirtualNodeRequest
    ): HTTPCreateVirtualNodeResponse
}