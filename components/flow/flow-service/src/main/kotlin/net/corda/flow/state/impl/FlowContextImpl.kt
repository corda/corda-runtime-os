package net.corda.flow.state.impl

import net.corda.flow.state.FlowContext

/**
 * The FlowContextImpl doesn't have its own Maps because that would present a problem when trying to suspend and resume
 * the executing Fiber. The storage is entirely backed by the FlowStack. This class must remain stateless.
 */
class FlowContextImpl(
    private val flowStack: FlowStackImpl
) : FlowContext {
    override fun set(key: String, value: String) {
        checkNotNull(flowStack.peek()) { "Attempt to set context before any items added to flow stack" }
            .contextUserProperties[key] = value
    }

    private fun getContextPlatformProperty(key: String): String? {
        flowStack.flowStackItems.asReversed().forEach { stackItem ->
            val value = stackItem.contextPlatformProperties.get(key)
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun getContextUserProperty(key: String): String? {
        flowStack.flowStackItems.asReversed().forEach { stackItem ->
            val value = stackItem.contextUserProperties.get(key)
            if (value != null) {
                return value
            }
        }
        return null
    }

    override fun get(key: String): String? =
        getContextPlatformProperty(key) ?: getContextUserProperty(key)

    override fun flattenPlatformProperties(): Map<String, String> {
        var flattenedProperties = emptyMap<String, String>()
        flowStack.flowStackItems.forEach { stackItem ->
            // Later values overwrite, which is what we want as we iterate from the beginning of the stack to the end
            flattenedProperties += stackItem.contextPlatformProperties
        }
        return flattenedProperties
    }

    override fun flattenUserProperties(): Map<String, String> {
        var flattenedProperties = emptyMap<String, String>()
        flowStack.flowStackItems.forEach { stackItem ->
            // Later values overwrite, which is what we want as we iterate from the beginning of the stack to the end
            flattenedProperties += stackItem.contextUserProperties
        }
        return flattenedProperties
    }
}
