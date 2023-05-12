package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface CacheEviction {
    fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>?

    @Throws(InterruptedException::class)
    fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean

    fun addEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean
    fun removeEvictionListener(type: SandboxGroupType, listener: EvictionListener): Boolean
}
