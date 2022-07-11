package net.corda.flow.rpcops.v1

import net.corda.flow.rpcops.v1.types.response.HTTPStartableFlowsResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource

/** RPC operations for getting flow information from a vNode. */
@HttpRpcResource(
    name = "FlowClassRPCOps",
    description = "Startable flow classes endpoint",
    path = "flowclass"
)
interface FlowClassRpcOps : RpcOps {

    @HttpRpcGET(
        path = "{holderShortId}",
        title = "Get Startable Flows",
        description = "Get all the flows that are startable for this holding identity.",
        responseDescription = "The class names of all flows that can be run"
    )
    fun getStartableFlows(
        @HttpRpcPathParameter(description = "Short form of the Holder Identifier")
        holderShortId: String
    ): HTTPStartableFlowsResponse
}