package net.corda.sandboxgroupcontext

import net.corda.sandbox.SandboxGroup

/**
 * A context object that is essentially a decorated [SandboxGroup].  It should contain everything required to
 * run a flow, or any other significant Corda operations.
 *
 * See [MutableSandboxGroupContext] for creating instances.
 *
 * You will want to use the [get] method (or its extensions) on a particular
 * [SandboxGroupContext] instance to get more complex objects and data from its object cache.
 *
 * The [VirtualNodeContext] member contains the unique key information that distinguishes a [SandboxGroupContext]
 * from another.
 */
interface SandboxGroupContext : SandboxGroupContextData, AutoCloseable {
    /**
     * Get an object from *this* [SandboxGroupContext] instance's object cache using the given key.
     *
     * You could use the extension method instead: [getObjectByKey]
     *
     * IMPORTANT:  caller must check `null` return - that's generally a sign you're looking for the wrong
     * key/type combo.
     *
     * @return null if it doesn't exist.
     */
    fun <T : Any> get(key: String, valueType: Class<out T>): T?
}
