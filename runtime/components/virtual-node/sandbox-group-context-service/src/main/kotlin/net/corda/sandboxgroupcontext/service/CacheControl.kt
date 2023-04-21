package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface CacheControl {
    fun initCaches(capacity: Long) = SandboxGroupType.values().forEach { initCache(it, capacity) }

    fun initCache(type: SandboxGroupType, capacity: Long)
    fun flushCache(): CompletableFuture<*>
    fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>?

    @Throws(InterruptedException::class)
    fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean
}
