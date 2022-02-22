package net.corda.flow.rpcops.v1.response

/**
 * The response from a call to start flow.

 * @param isExistingFlow True if a flow with the same Client Request ID exists for the given Holding ID.
 * @param flowStatus Flow status object containing the current state of the flow.
 */
data class HTTPStartFlowResponse(
    val isExistingFlow: Boolean,
    val flowStatus: HTTPFlowStatusResponse,
)

