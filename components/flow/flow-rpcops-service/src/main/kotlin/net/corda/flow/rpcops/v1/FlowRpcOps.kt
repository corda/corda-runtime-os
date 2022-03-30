package net.corda.flow.rpcops.v1

import net.corda.flow.rpcops.v1.response.HTTPFlowStatusResponse
import net.corda.flow.rpcops.v1.response.HTTPStartFlowResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPUT
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.libs.configuration.SmartConfig

/** RPC operations for flow management. */
@HttpRpcResource(
    name = "FlowRPCOps",
    description = "Flow management endpoints",
    path = "flow"
)
interface FlowRpcOps : RpcOps {

    /**
     * Initialises the API implementation. This method may be called multiple times throughout the life
     * of the API.
     */
    fun initialise(config: SmartConfig)

    @HttpRpcPUT(
        path = "{holderShortId}/{clientRequestId}/{flowClassName}",
        title = "Start Flow",
        description = "Instructs Corda to start a new instance of the specified flow",
        responseDescription = "The initial status of the flow, if the flow already exists the status of the existing" +
                " flow will be returned."
    )
    fun startFlow(
        @HttpRpcPathParameter(description = "Short form of the Holder Identifier")
        holderShortId: String,
        @HttpRpcPathParameter(description = "Client provided flow identifier")
        clientRequestId: String,
        @HttpRpcPathParameter(description ="Fully qualified class name of the flow to start.")
        flowClassName: String,
        @HttpRpcRequestBodyParameter(description = "Optional start arguments string passed to the flow.", required = false)
        requestBody: String
    ): HTTPStartFlowResponse

    @HttpRpcGET(
        path = "{holderShortId}/{clientRequestId}",
        title = "Get Flow Status",
        description = "Gets the current status for a given flow.",
        responseDescription = "The status of the flow."
    )
    fun getFlowStatus(
        @HttpRpcPathParameter(description = "Short form of the Holder Identifier")
        holderShortId: String,
        @HttpRpcPathParameter(description = "Client provided flow identifier")
        clientRequestId: String
    ) : HTTPFlowStatusResponse
}