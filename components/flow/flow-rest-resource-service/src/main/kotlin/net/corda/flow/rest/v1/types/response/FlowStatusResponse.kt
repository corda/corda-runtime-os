package net.corda.flow.rest.v1.types.response

import java.time.Instant

/**
 * The status of a flow.
 *
 * @param holdingIdentityShortHash The short form hash of the Holding Identity
 * @param clientRequestId The unique ID supplied by the client when the flow was created.
 * @param flowId The internal unique ID for the flow.
 * @param flowStatus The current state of the executing flow.
 * @param flowResult The result returned from a completed flow, only set when the flow status is 'COMPLETED' otherwise
 * null
 * @param flowError The details of the error that caused a flow to fail, only set when the flow status is 'FAILED'
 * otherwise null
 * @param timestamp The timestamp of when the status was last updated (in UTC)
 */
data class FlowStatusResponse(
    val holdingIdentityShortHash: String,
    val clientRequestId: String?,
    val flowId: String?,
    val flowStatus: String,
    val flowResult: String?,
    val flowError: FlowStateErrorResponse?,
    val timestamp: Instant
)
