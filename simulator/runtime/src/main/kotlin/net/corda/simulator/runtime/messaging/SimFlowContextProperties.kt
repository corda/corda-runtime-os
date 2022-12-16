package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.base.exceptions.CordaRuntimeException

/*
    The simulator version of flow context properties to be passed to the flows
 */
class SimFlowContextProperties(
    userPropertiesMap : Map<String, String>
): FlowContextProperties {
    private val userContextProperties = userPropertiesMap.toMutableMap()

    override fun get(key: String): String? {
        return userContextProperties[key]
    }

    override fun put(key: String, value: String) {
        require(!key.lowercase().startsWith(FlowContextProperties.CORDA_RESERVED_PREFIX)) {
            "'${key}' starts with '${FlowContextProperties.CORDA_RESERVED_PREFIX}' which is reserved for Corda platform properties"
        }

        userContextProperties[key] = value
    }

    fun copy() = SimFlowContextProperties(this.userContextProperties)

    fun toImmutableContext() = SimImmutableFlowContextProperties(this.userContextProperties.toMap())

    override fun equals(other: Any?): Boolean {
        if(other == null || other !is SimFlowContextProperties)
            return false

        return userContextProperties == other.userContextProperties
    }

    override fun hashCode(): Int {
        return userContextProperties.hashCode()
    }

}

/*
    Immutable Flow Context Properties
 */
class SimImmutableFlowContextProperties(
    private val userPropertiesMap : Map<String, String>
):FlowContextProperties{
    override fun get(key: String): String? {
        return userPropertiesMap[key]
    }

    override fun put(key: String, value: String) {
        throw CordaRuntimeException("This operation is not supported, these context properties are read only")
    }
}

fun copyFlowContextProperties(contextProperties: FlowContextProperties) : FlowContextProperties {
    return (contextProperties as SimFlowContextProperties).copy()
}