package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.VirtualNodeContext
import java.time.Duration
import java.util.concurrent.CompletableFuture

interface CacheControl {
    fun initCache(capacity: Long)
    fun flushCache(): CompletableFuture<*>
    fun remove(virtualNodeContext: VirtualNodeContext): CompletableFuture<*>?

    @Throws(InterruptedException::class)
    fun waitFor(completion: CompletableFuture<*>, duration: Duration): Boolean
}
