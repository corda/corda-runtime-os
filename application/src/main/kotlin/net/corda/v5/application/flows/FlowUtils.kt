@file:JvmName("FlowUtils")
package net.corda.v5.application.flows

/**
 * Puts a user value into the context property store.
 *
 * Setting user properties with the same key as existing platform properties throws an [IllegalArgumentException].
 *
 * It is highly advisable to scope user property keys with some unique prefix, e.g. package name. Corda's platform keys
 * are usually prefixed with [CORDA_RESERVED_PREFIX]. This is reserved and an attempt to prefix a user key with
 * [CORDA_RESERVED_PREFIX] also throws an [IllegalArgumentException], whether the key exists already or not.
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
 * @param key The property key.
 * @param value The property value.
 *
 * @throws IllegalArgumentException If a platform property already exists for this key or if the key is prefixed by
 * [CORDA_RESERVED_PREFIX].
 */
operator fun FlowContextProperties.set(key: String, value: String) = put(key, value)
