package net.corda.flow.rpcops.v1.types.request

import net.corda.httprpc.JsonObject

/**
 * Request sent to start a flow
 *
 * @param clientRequestId Client provided flow identifier
 * @param flowClassName Fully qualified class name of the flow to start.
 * @param requestData Optional start arguments string passed to the flow. Defaults to empty object.
 */
data class StartFlowParameters(
    val clientRequestId: String,
    val flowClassName: String,
    val requestData: JsonObject
)
