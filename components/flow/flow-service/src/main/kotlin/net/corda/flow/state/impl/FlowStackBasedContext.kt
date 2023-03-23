package net.corda.flow.state.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.flow.utils.KeyValueStore
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.FlowContextProperties.CORDA_RESERVED_PREFIX

/**
 * [FlowStackBasedContext] is the core means of interacting with flow context internally in Corda. It is stack based
 * context, entirely backed by the FlowStack. This class must remain stateless such that suspending and resuming the
 * stack correctly re-establishes the stack based context.
 */
class FlowStackBasedContext(
    private val flowStack: FlowStackImpl
) : FlowContext, NonSerializable {

    override val platformProperties = object : ContextPlatformProperties {
        override fun put(key: String, value: String) {
            val platformContextKeyValueStore = KeyValueStore(
                checkNotNull(flowStack.peek())
                { "Attempt to set context before any items added to flow stack" }.contextPlatformProperties
            )
            platformContextKeyValueStore[key] = value
        }
    }

    override fun put(key: String, value: String) {
        val userContextKeyValueStore = KeyValueStore(
            checkNotNull(flowStack.peek())
            { "Attempt to set context before any items added to flow stack" }.contextUserProperties
        )

        require(getPropertyFromPlatformStack(key) == null) {
            "'${key}' is already a platform context property, it cannot be overwritten with a user property"
        }

        require(!key.lowercase().startsWith(CORDA_RESERVED_PREFIX)) {
            "'${key}' starts with '${CORDA_RESERVED_PREFIX}' which is reserved for Corda platform properties"
        }

        userContextKeyValueStore[key] = value
    }

    override fun get(key: String): String? = getPropertyFromPlatformStack(key) ?: getPropertyFromUserStack(key)

    private fun getPropertyFromUserStack(key: String) =
        getPropertyFromStack(key) { flowStackItem -> flowStackItem.contextUserProperties }

    private fun getPropertyFromPlatformStack(key: String) =
        getPropertyFromStack(key) { flowStackItem -> flowStackItem.contextPlatformProperties }

    /**
     * Return property searching the stack
     * @param key The key to search for
     * @param block Lambda which takes a stack item and requires the return of a list from that stack item in which to
     * search for the key
     */
    private fun getPropertyFromStack(key: String, block: (FlowStackItem) -> KeyValuePairList): String? {
        flowStack.flowStackItems.asReversed().forEach { stackItem ->
            KeyValueStore(block(stackItem))[key]?.let { return it }
        }
        return null
    }

    override fun flattenPlatformProperties(): Map<String, String> {
        return flowStack.flowStackItems.flatMapLaterKeysOverwrite { stackItem -> stackItem.contextPlatformProperties }
    }

    override fun flattenUserProperties(): Map<String, String> {
        return flowStack.flowStackItems.flatMapLaterKeysOverwrite { stackItem -> stackItem.contextUserProperties }
    }

    private fun List<FlowStackItem>.flatMapLaterKeysOverwrite(block: (FlowStackItem) -> KeyValuePairList): Map<String, String> {
        val flattenedKeyValueStore = mutableMapOf<String, String>()
        this.forEach { stackItem ->
            val stackItemKeyValueStore = KeyValueStore(block(stackItem))
            // We iterate from the beginning of the stack to the end, so later values (those closer to the current stack
            // item) overwrite previous ones
            flattenedKeyValueStore += stackItemKeyValueStore
        }

        return flattenedKeyValueStore
    }

    private operator fun MutableMap<String, String>.plusAssign(toAdd: KeyValueStore) {
        toAdd.avro.items.forEach { keyValuePair ->
            this[keyValuePair.key] = keyValuePair.value
        }
    }
}
