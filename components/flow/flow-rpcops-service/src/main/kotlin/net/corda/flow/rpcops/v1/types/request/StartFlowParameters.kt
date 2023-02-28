package net.corda.flow.rpcops.v1.types.request

import net.corda.rest.JsonObject

/**
 * Request sent to start a flow
 *
 * @param clientRequestId Client provided flow identifier
 * @param flowClassName Fully qualified class name of the flow to start.
 * @param requestBody Optional start arguments string passed to the flow. Defaults to empty string.
 */
data class StartFlowParameters(
    val clientRequestId: String,
    val flowClassName: String,
    val requestBody: JsonObject
)
