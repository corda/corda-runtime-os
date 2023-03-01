package net.corda.flow.rest.v1.types.response

/**
 * The status of all flows for a single holding identity
 *
 * @param flowStatusResponses List of [FlowStatusResponse]. Empty if there are no flows for this holding id.
 */
data class FlowStatusResponses(
    val flowStatusResponses: List<FlowStatusResponse>
)