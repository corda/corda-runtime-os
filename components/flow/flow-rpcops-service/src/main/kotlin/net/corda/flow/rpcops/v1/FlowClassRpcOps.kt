package net.corda.flow.rpcops.v1

import net.corda.flow.rpcops.v1.types.response.StartableFlowsResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource

/** RPC operations for getting flow information from a vNode. */
@HttpRpcResource(
    name = "Flow Info API",
    description = "Startable flow classes endpoints.",
    path = "flowclass"
)
interface FlowClassRpcOps : RpcOps {

    @HttpRpcGET(
        path = "{holdingIdentityShortHash}",
        title = "Get Startable Flows",
        description = "Get all the flows that are startable for this holding identity.",
        responseDescription = "The class names of all flows that can be run"
    )
    fun getStartableFlows(
        @HttpRpcPathParameter(description = "Short hash of the holding identity")
        holdingIdentityShortHash: String
    ): StartableFlowsResponse
}
