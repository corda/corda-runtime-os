package net.corda.flow.rpcops.v1

import net.corda.flow.rpcops.v1.types.request.StartFlowParameters
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponses
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
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

    @HttpRpcPOST(
        path = "{holdingIdentityShortHash}",
        title = "Start Flow",
        description = "Instructs Corda to start a new instance of the specified flow",
        responseDescription = "The initial status of the flow, if the flow already exists the status of the existing" +
                " flow will be returned."
    )
    fun startFlow(
        @HttpRpcPathParameter(description = "Short hash of the holding identity")
        holdingIdentityShortHash: String,
        @HttpRpcRequestBodyParameter(description = "Information required to start a flow for this holdingId", required = true)
        startFlow: StartFlowParameters
    ): FlowStatusResponse

    @HttpRpcGET(
        path = "{holdingIdentityShortHash}/{clientRequestId}",
        title = "Get Flow Status",
        description = "Gets the current status for a given flow.",
        responseDescription = "The status of the flow."
    )
    fun getFlowStatus(
        @HttpRpcPathParameter(description = "Short hash of the holding identity")
        holdingIdentityShortHash: String,
        @HttpRpcPathParameter(description = "Client provided flow identifier")
        clientRequestId: String
    ): FlowStatusResponse

    @HttpRpcGET(
        path = "{holdingIdentityShortHash}",
        title = "Get Multiple Flow Status",
        description = "Get status of all flows for a holding identity. Returns an empty list if there are no flows running.",
        responseDescription = "The status of the flow."
    )
    fun getMultipleFlowStatus(
        @HttpRpcPathParameter(description = "Short hash of the holding identity")
        holdingIdentityShortHash: String
    ): FlowStatusResponses
}
