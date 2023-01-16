package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of [MutableSandboxGroupContext] and [SandboxGroupContext]
 *
 * Holds the "context" of a virtual node.
 *
 * The "core" information is in [VirtualNodeContext], and additional information can be found via the [get] method.
 */
class SandboxGroupContextImpl(
    override val virtualNodeContext: VirtualNodeContext,
    override val sandboxGroup: SandboxGroup
) :  MutableSandboxGroupContext {

    private val objectByKey = ConcurrentHashMap<String, Any>()

    override val completion: CompletableFuture<Boolean> = CompletableFuture()

    override fun <T : Any> put(key: String, value: T) {
        if (objectByKey.putIfAbsent(key, value) != null) {
            throw IllegalArgumentException("Attempt to overwrite existing object in cache with key: $key")
        }
    }

    override fun <T : Any> get(key: String, valueType: Class<out T>): T? {
        return objectByKey[key]?.let(valueType::cast)
    }
}
