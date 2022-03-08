package net.corda.flow.rpcops.v1.response

/**
 * Represents the error for a flow that is in the FAILED state.
 *
 * @param type The type of error.
 * @param message The details of the error and its cause.
 */
data class HTTPFlowStateErrorResponse(
    val type: String,
    val message: String
)