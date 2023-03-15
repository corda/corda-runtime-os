package net.corda.flow.state.impl

import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.v5.application.flows.FlowContextProperties.CORDA_RESERVED_PREFIX

/**
 * A [FlatSerializableContext] which supports put operations.
 */
class MutableFlatSerializableContext(
    contextUserProperties: Map<String, String>, contextPlatformProperties: Map<String, String>
) : FlatSerializableContext(contextUserProperties, contextPlatformProperties), FlowContext {
    override fun put(key: String, value: String) {
        require(platformPropertyMap[key] == null) {
            "'${key}' is already a platform context property, it cannot be overwritten with a user property"
        }

        require(!key.lowercase().startsWith(CORDA_RESERVED_PREFIX)) {
            "'${key}' starts with '${CORDA_RESERVED_PREFIX}' which is reserved for Corda platform properties"
        }

        userPropertyMap[key] = value
    }

    override val platformProperties: ContextPlatformProperties = object : ContextPlatformProperties {
        override fun put(key: String, value: String) {
            platformPropertyMap[key] = value
        }
    }
}
