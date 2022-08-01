package net.corda.flow.state

import net.corda.v5.application.flows.FlowContextProperties

interface FlowContext : FlowContextProperties {
    /**
     * Returns all platfrom properties in the form of a map
     */
    fun flattenPlatformProperties(): Map<String, String>

    /**
     * Returns all user properties in the form of a map
     */
    fun flattenUserProperties(): Map<String, String>
}