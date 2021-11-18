package net.corda.virtual.node.sandboxgroup

interface MutableSandboxGroupContext : SandboxGroupContext {
    /**
     * Put an object into the cache.
     */
    fun <T> put(key: String, value: T)
}
