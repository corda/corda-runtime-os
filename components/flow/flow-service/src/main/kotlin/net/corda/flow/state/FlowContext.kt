package net.corda.flow.state

import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.Flow
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

    /**
     * An accessor for setting platform properties. Setting platform properties cannot be  part of
     * [FlowContextProperties] because setting platform properties is prohibited for user code. There is no specific
     * accessor for getting platform properties, the publicly accessible [FlowContextProperties] methods should be used
     * for this.
     */
    val platformProperties: PlatformProperties

    /**
     * A map like interface for setting platform properties.
     */
    interface PlatformProperties {
        /**
         * Puts a platform value into the context property store.
         *
         * Where keys exist as platform properties already, trying to set the value of the same key again will throw. It
         * is highly advisable to scope platform property keys with the prefix "corda." and some other uniqueness to the
         * package setting the key, meaning that an attempt to prefix a user key with the same key will also throw.
         *
         * Both sets of context properties (user and platform) are propagated automatically from the originating [Flow]
         * to all sub-flows initiated flows, and services. Where sub-flows and initiated flows have extra properties
         * added, these are only visible in the scope of those flows and any of their sub-flows, initiated flows or
         * services, but not back up the flow stack to any flows which launched or initiated those flows.
         *
         * @param key The property key
         * @param value The property value
         * @throws IllegalArgumentException if a platform property already exists for this key.
         */
        fun put(key: String, value: String)

        /**
         * Puts a platform value into the context property store.
         *
         * Where keys exist as platform properties already, trying to set the value of the same key again will throw. It
         * is highly advisable to scope platform property keys with the prefix "corda." and some other uniqueness to the
         * package setting the key, meaning that an attempt to prefix a user key with the same key will also throw.
         *
         * Both sets of context properties (user and platform) are propagated automatically from the originating [Flow]
         * to all sub-flows initiated flows, and services. Where sub-flows and initiated flows have extra properties
         * added, these are only visible in the scope of those flows and any of their sub-flows, initiated flows or
         * services, but not back up the flow stack to any flows which launched or initiated those flows.
         *
         * Opens up [] operator access for setting values in Kotlin.
         *
         * @param key The property key
         * @param value The property value
         * @throws IllegalArgumentException if a platform property already exists for this key.
         */
        operator fun set(key: String, value: String) = put(key, value)
    }
}
