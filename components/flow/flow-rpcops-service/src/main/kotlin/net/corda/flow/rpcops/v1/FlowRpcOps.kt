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
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.response.ResponseEntity
import net.corda.httprpc.ws.DuplexChannel
import net.corda.libs.configuration.SmartConfig

/** RPC operations for flow management. */
@HttpRpcResource(
    name = "Flow Management API",
    description = "The Flow Management API consists of a number of endpoints used to interact with flows.",
    path = "flow"
)
interface FlowRpcOps : RpcOps {

    /**
     * Initialises the API implementation. This method may be called multiple times throughout the life
     * of the API.
     * @param config A config
     * @param onFatalError A method the [FlowRpcOps] will call if a fatal error is generated internally. This method hands
     * over responsibility to the client on how to handle fatal errors. The method may handle fatal errors asynchronously.
     * The [FlowRpcOps] will ensure it is left in a state where it is inoperable functionally after a fatal error, but
     * safe to interact with.
     */
    fun initialise(config: SmartConfig, onFatalError: () -> Unit)

    @HttpRpcPOST(
        path = "{holdingIdentityShortHash}",
        title = "Start Flow",
        description = "This method starts a new instance for the specified flow for the specified holding identity.",
        responseDescription = """
            The initial status of the flow instance; if the flow already exists, then the status of the existing flow will be returned.
            
            holdingIdentityShortHash: The short form hash of the holding identity
            clientRequestId: The unique ID supplied by the client when the flow was created.
            flowId: The internal unique ID for the flow.
            flowStatus: The current state of the executing flow.
            flowResult: The result returned from a completed flow, only set when the flow status is 'COMPLETED' otherwise null
            flowError: The details of the error that caused a flow to fail, only set when the flow status is 'FAILED' otherwise null
            timestamp: The timestamp of when the status was last updated (in UTC)
            """
    )
    fun startFlow(
        @HttpRpcPathParameter(description = "The short hash of the holding identity; obtained during node registration")
        holdingIdentityShortHash: String,
        @HttpRpcRequestBodyParameter(
            description = """
                Information required to start a flow for this holdingId, including:
                clientRequestId: a client provided flow identifier
                flowClassName: fully qualified class name of the flow to start
                requestBody: optional start arguments string passed to the flow; defaults to an empty string
            """,
            required = true
        )
        startFlow: StartFlowParameters
    ): ResponseEntity<FlowStatusResponse>

    @HttpRpcGET(
        path = "{holdingIdentityShortHash}/{clientRequestId}",
        title = "Get Flow Status",
        description = "This method gets the current status of the specified flow instance.",
        responseDescription = """
            The status of the flow instance, including:
            
            holdingIdentityShortHash: The short form hash of the Holding Identity
            clientRequestId: The unique ID supplied by the client when the flow was created.
            flowId: The internal unique ID for the flow.
            flowStatus: The current state of the executing flow.
            flowResult: The result returned from a completed flow, only set when the flow status is 'COMPLETED' otherwise null
            flowError: The details of the error that caused a flow to fail, only set when the flow status is 'FAILED' otherwise null
            timestamp: The timestamp of when the status was last updated (in UTC)
            """
    )
    fun getFlowStatus(
        @HttpRpcPathParameter(description = "The short hash of the holding identity; obtained during node registration")
        holdingIdentityShortHash: String,
        @HttpRpcPathParameter(description = "Client provided flow identifier")
        clientRequestId: String
    ): FlowStatusResponse

    @HttpRpcGET(
        path = "{holdingIdentityShortHash}",
        title = "Get Multiple Flow Status",
        description = "This method returns an array containing the statuses of all flows running for a specified " +
                "holding identity. An empty array is returned if there are no flows running.",
        responseDescription = """
            A collection of statuses for the flow instances, including:
            
            holdingIdentityShortHash: The short form hash of the Holding Identity
            clientRequestId: The unique ID supplied by the client when the flow was created.
            flowId: The internal unique ID for the flow.
            flowStatus: The current state of the executing flow.
            flowResult: The result returned from a completed flow, only set when the flow status is 'COMPLETED' otherwise null
            flowError: The details of the error that caused a flow to fail, only set when the flow status is 'FAILED' otherwise null
            timestamp: The timestamp of when the status was last updated (in UTC)
            """
    )
    fun getMultipleFlowStatus(
        @HttpRpcPathParameter(description = "The short hash of the holding identity; obtained during node registration")
        holdingIdentityShortHash: String
    ): FlowStatusResponses

    @HttpRpcWS(
        path = "{holdingIdentityShortHash}/{clientRequestId}",
        title = "Get status updates for a flow via websockets.",
        description = "Gets a stream of status updates for a given flow.",
        responseDescription = "Flow status updates."
    )
    fun registerFlowStatusUpdatesFeed(
        channel: DuplexChannel,
        @HttpRpcPathParameter(description = "The short hash of the holding identity; obtained during node registration")
        holdingIdentityShortHash: String,
        @HttpRpcPathParameter(description = "Client provided flow identifier")
        clientRequestId: String
    )
}
