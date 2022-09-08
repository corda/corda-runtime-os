package net.corda.flow.external.events.impl.executor

/**
 * Map context properties sent from a flow to an external processor.
 * Whilst context is propagated down the chain of flows, it can be tweaked across the flow/external processor boundary.
 * The context presented to an external processor does not need to be an exact replica of context from the initiating
 * party, instead it can contain more pertinent information.
 *
 * @param userContextProperties User context properties of the current flow
 * @param platformContextProperties Platform context properties of the current flow
 *
 * @return Context properties for an external event translated to a single map. External processors do not need to
 * understand the distinction between user and platform properties, being only readers of context.
 */
fun localToExternalContextMapper(
    userContextProperties: Map<String, String>,
    platformContextProperties: Map<String, String>
): Map<String, String> {
    return platformContextProperties + userContextProperties
}
