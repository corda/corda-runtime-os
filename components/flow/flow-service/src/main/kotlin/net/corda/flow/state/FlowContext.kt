package net.corda.flow.state

import net.corda.data.KeyValuePairList
import net.corda.v5.application.flows.FlowContextProperties

/**
 * This interface extends the CorDapp creator facing [FlowContextProperties] with Corda internal facing methods. It acts
 * as an abstraction to the implementation of flow context which can be used internally withing Corda. It extends the
 * available functionality of flow context beyond the narrow subset of user context exposed to application writers.
 */
interface FlowContext : FlowContextProperties {
    /**
     * Returns all platform properties in a single container
     */
    fun flattenPlatformProperties(): KeyValuePairList

    /**
     * Returns all user properties in a single container
     */
    fun flattenUserProperties(): KeyValuePairList
}
