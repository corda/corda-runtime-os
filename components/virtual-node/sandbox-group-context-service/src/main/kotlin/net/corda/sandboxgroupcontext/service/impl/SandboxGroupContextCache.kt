package net.corda.sandboxgroupcontext.service.impl

import java.time.Duration
import java.util.concurrent.CompletableFuture
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext

interface SandboxGroupContextCache : AutoCloseable {
    val capacity: Long
    fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>?
    fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext

    fun resize(newCapacity: Long): SandboxGroupContextCache
    fun flush(): CompletableFuture<*>

    @Throws(InterruptedException::class)
    fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean
}
