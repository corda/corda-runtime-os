package net.corda.flow.state.impl

import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * A set of context properties which implements the same interfaces as the core flow context implemented by
 * [FlowStackBasedContext], except is non-stack based and serializable. This makes it suitable to be held as a property
 * of classes which need to allow the user to add extra context properties to a snapshot of the core set, but know they
 * are also required to be accessed by suspendable fibers.
 *
 * This base class does not support put operations.
 */
open class FlatSerializableContext(
    contextUserProperties: Map<String, String>, contextPlatformProperties: Map<String, String>
) : FlowContext {

    protected val userPropertyMap = contextUserProperties.toMutableMap()
    protected val platformPropertyMap = contextPlatformProperties.toMutableMap()

    override fun put(key: String, value: String): Unit = throwNotSupported()

    override fun get(key: String): String? = platformPropertyMap[key] ?: userPropertyMap[key]

    override fun flattenPlatformProperties() = platformPropertyMap.toMap()

    override fun flattenUserProperties() = userPropertyMap.toMap()

    override val platformProperties: ContextPlatformProperties = object : ContextPlatformProperties {
        override fun put(key: String, value: String): Unit = throwNotSupported()
    }

    private fun throwNotSupported(): Nothing =
        throw CordaRuntimeException("This operation is not supported, these context properties are read only")
}
