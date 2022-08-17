package net.corda.flow.state.impl

import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.v5.application.flows.FlowContextProperties.Companion.CORDA_RESERVED_PREFIX

/**
 * A set of context properties which implements the same interfaces as the core flow context implemented by
 * [FlowStackBasedContext], except is non-stack based and serializable. This makes it suitable to be held as a property
 * of classes which need to allow the user to add extra context properties to a snapshot of the core set, but know they
 * are also required to be accessed by suspendable fibers.
 */
class FlatSerializableContext(
    contextUserProperties: Map<String, String>, contextPlatformProperties: Map<String, String>
) : FlowContext {

    private val userPropertyMap = contextUserProperties.toMutableMap()
    private val platformPropertyMap = contextPlatformProperties.toMutableMap()

    override fun put(key: String, value: String) {
        require(platformPropertyMap[key] == null) {
            "'${key}' is already a platform context property, it cannot be overwritten with a user property"
        }

        require(!key.lowercase().startsWith(CORDA_RESERVED_PREFIX)) {
            "'${key}' starts with '${CORDA_RESERVED_PREFIX}' which is reserved for Corda platform properties"
        }

        userPropertyMap[key] = value
    }

    override fun get(key: String): String? = platformPropertyMap[key] ?: userPropertyMap[key]

    override fun flattenPlatformProperties() = platformPropertyMap.toMap()

    override fun flattenUserProperties() = userPropertyMap.toMap()

    override val platformProperties: ContextPlatformProperties = object : ContextPlatformProperties {
        override fun put(key: String, value: String) {
            require(platformPropertyMap[key] == null) {
                "'${key}' is already a platform context property, it cannot be overwritten"
            }
            platformPropertyMap[key] = value
        }
    }
}
