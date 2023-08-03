package net.corda.flow.rest.v1.types.response

/**
 * List of flows that can be run.
 *
 * @param flowClassNames List of flow class names.
 */
@Deprecated("Deprecated, unused in newer endpoint getAllStartableFlowsList, remove once out of LTS")
data class StartableFlowsResponse(
    val flowClassNames: List<String>
)