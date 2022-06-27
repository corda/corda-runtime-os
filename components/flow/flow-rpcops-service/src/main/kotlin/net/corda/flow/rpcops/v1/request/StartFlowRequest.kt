package net.corda.flow.rpcops.v1.request

/**
 * Request sent to start a flow
 *
 * @param clientRequestId Client provided flow identifier
 * @param flowClassName Fully qualified class name of the flow to start.
 * @param flowParams Optional start arguments string passed to the flow. Defaults to empty string.
 */
data class StartFlowRequest(
    val clientRequestId: String,
    val flowClassName: String,
    val flowParams: String = ""
)
