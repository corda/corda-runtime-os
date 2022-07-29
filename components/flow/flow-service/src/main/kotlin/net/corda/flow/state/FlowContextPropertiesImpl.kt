package net.corda.flow.state

import net.corda.v5.application.flows.FlowContextProperties

/**
 * The FlowContextPropertiesImpl doesn't have its own Maps because that would present a problem when trying to suspend
 * and resume the executing Fiber. Instead it should be fed Maps from the Checkpoint. This class then only contains the
 * logic require to realise the FlowContextProperties interface, it's doesn't "own" the data.
 */
class FlowContextPropertiesImpl(
    private val platformProperties: MutableMap<String, String>,
    private val userProperties: MutableMap<String, String>
) : FlowContextProperties {

    // TODO temp impl for testing?

    override fun set(key: String, value: String) {
        userProperties.put(key, value)
    }

    override fun get(key: String): String? {
        var property = platformProperties.get(key)
        if (property == null) {
            property = userProperties.get(key)
        }
        return property
    }

    /**
     * Any properties set after this point will be popped off the context when the corresponding call to popStackMarker
     * is made.
     */
    fun pushStackMarker() {
        // TODO
    }

    /**
     * Pop all properties that were added since the last call to pushStackMarker off the context.
     */
    fun popStackMarker() {
        // TODO
    }

    /**
     * Returns all platfrom properties in the form of a map
     */
    fun flattenPlatformProperties(): Map<String, String> = platformProperties

    /**
     * Returns all user properties in the form of a map
     */
    fun flattenUserProperties(): Map<String, String> = userProperties
}
