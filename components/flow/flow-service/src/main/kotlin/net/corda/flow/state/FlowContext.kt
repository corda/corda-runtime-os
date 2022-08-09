package net.corda.flow.state

import net.corda.data.KeyValuePairList
import net.corda.v5.application.flows.FlowContextProperties

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
