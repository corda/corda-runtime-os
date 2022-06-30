package net.corda.flow.rpcops.v1.response

/**
 * The status of all flows for a single holding identity
 *
 * @param httpFlowStatusResponses List of [HTTPFlowStatusResponse]. Empty if there are no flows for this holding id.
 */
data class HTTPFlowStatusResponses(
    val httpFlowStatusResponses: List<HTTPFlowStatusResponse>
)
