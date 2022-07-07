package net.corda.flow.rpcops.v1.types.response

/**
 * List of flows that can be run.
 *
 * @param flowClassNames List of flow class names.
 */
data class HTTPStartableFlowsResponse(
    val flowClassNames: List<String>
)