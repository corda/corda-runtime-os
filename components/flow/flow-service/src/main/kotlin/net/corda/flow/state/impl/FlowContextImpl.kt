package net.corda.flow.state.impl

import net.corda.data.KeyValuePairList
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.state.FlowContext
import net.corda.flow.utils.KeyValueStore

/**
 * The FlowContextImpl doesn't have its own KeyValuePairLists because that would present a problem when trying to
 * suspend and resume the executing Fiber. The storage is entirely backed by the FlowStack. This class must remain
 * stateless.
 */
class FlowContextImpl(
    private val flowStack: FlowStackImpl
) : FlowContext {

    companion object {
        const val CORDA_RESERVED_PREFIX = "corda." // must be lowercase
    }

    override fun put(key: String, value: String) {
        require(getPropertyFromPlatformStack(key) == null) {
            "'${key}' is already a platform context property, it cannot be overwritten with a user property"
        }

        require(!key.lowercase().startsWith(CORDA_RESERVED_PREFIX)) {
            "'${key}' starts with '${CORDA_RESERVED_PREFIX}' which is reserved for Corda platform properties"
        }

        val userContextKeyValueStore = KeyValueStore(
            checkNotNull(flowStack.peek())
            { "Attempt to set context before any items added to flow stack" }.contextUserProperties
        )

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
