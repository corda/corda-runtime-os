@file:JvmName("FlowUtils")
package net.corda.v5.application.flows

/**
 * Context properties of a [Flow] are key value pairs of Strings. They are comprised of two sets of properties, those
 * set by the platform when a [Flow] is started, and those which are added by the CorDapp developer during the execution
 * of their flow. The latter set are referred to as user context properties. All context properties are immutable once
 * set.
 */
interface FlowContextProperties {
    /**
     * Puts a user value into the context property store.
     *
     * Where keys exist as platform properties already, setting user properties with the same key will throw. It is
     * highly advisable to scope user property keys with some unique prefix, e.g. package name. Corda's platform keys
     * will usually be prefixed with "corda." which is reserved, meaning that an attempt to prefix a user key with
     * "corda." will also throw whether it exists already or not.
     *
     * Both sets of context properties are propagated automatically from the originating [Flow] to all sub-flows
     * initiated flows, and services. Where sub-flows and initiated flows have extra user properties added, these are
     * only visible in the scope of those flows and any of their sub-flows, initiated flows or services, but not back up
     * the flow stack to any flows which launched or initiated those flows. The same is true of overwritten user
     * properties. These properties are overwritten for the current flow and any flows initiated or instantiated by that
     * flow, and also any further down the chain. When execution returns to a flow higher up the stack (a parent or one
     * that initiated it) the overwritten properties are not visible.
     *
     * @param key The property key
     * @param value The property value
     * @throws IllegalArgumentException if a platform property already exists for this key or if the key is prefixed by
     * "corda."
     */
    fun put(key: String, value: String)

    /**
     * Gets a value from the context property store.
     * Also opens up [] operator access for getting values in Kotlin.
     *
     * @param key The property key
     * @return The property value
     */
    operator fun get(key: String): String?
}

/**
 * Puts a user value into the context property store.
 *
 * Where keys exist as platform properties already, setting user properties with the same key will throw. It is highly
 * advisable to scope user property keys with some unique prefix, e.g. package name. Corda's platform keys will usually
 * be prefixed with "corda." which is reserved, meaning that an attempt to prefix a user key with "corda." will also
 * throw whether it exists already or not.
 *
 * Both sets of context properties are propagated automatically from the originating [Flow] to all sub-flows, initiated
 * flows, and services. Where sub-flows and initiated flows have extra user properties added, these are only visible in
 * the scope of those flows and any of their sub-flows, initiated flows or services, but not back up the flow stack to
 * any flows which launched or initiated those flows. The same is true of overwritten user properties. These properties
 * are overwritten for the current flow and any flows initiated or instantiated by that flow, and also any further down
 * the chain. When execution returns to a flow higher up the stack (a parent or one that initiated it) the overwritten
 * properties are not visible.
 *
 * Opens up [] operator access for setting values in Kotlin.
 *
 * @param key The property key
 * @param value The property value
 * @throws IllegalArgumentException if a platform property already exists for this key or if the key is prefixed by
 * "corda."
 */
operator fun FlowContextProperties.set(key: String, value: String) = put(key, value)
