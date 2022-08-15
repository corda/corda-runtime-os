package net.corda.flow.state

import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.FlowContextProperties

/**
 * This interface extends the CorDapp creator facing [FlowContextProperties] with Corda internal facing methods. It acts
 * as an abstraction to the implementation of flow context which can be used internally withing Corda. It extends the
 * available functionality of flow context beyond the narrow subset of user context exposed to application writers.
 *
 * Flattening is done into standard Kotlin Map, so we don't expose Avro generated types to suspendable methods called
 * from CorDapp flows. These types are not serializable and can never end up in the stack of an executing flow.
 */
interface FlowContext : FlowContextProperties, NonSerializable {
    /**
     * Returns all platform properties in a single container
     * @return A map of platform properties
     */
    fun flattenPlatformProperties(): Map<String, String>

    /**
     * Returns all user properties in a single container
     * @return A map of platform properties
     */
    fun flattenUserProperties(): Map<String, String>
}
