package net.corda.flow.rpcops.v1.types.request

/**
 * Request object to terminate a websocket feed for flow status updates.
 *
 * @param clientRequestId Client provided flow identifier
 * @param holdingShortId short identifier for the holding identity
 */
data class WebSocketTerminateFlowStatusFeedType(
    val clientRequestId: String,
    val holdingShortId: String
)
