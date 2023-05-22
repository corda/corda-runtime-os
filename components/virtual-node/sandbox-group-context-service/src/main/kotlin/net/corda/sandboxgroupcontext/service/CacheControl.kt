package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.SandboxGroupType
import java.util.concurrent.CompletableFuture

interface CacheControl : CacheEviction {
    fun resizeCaches(capacity: Long) = SandboxGroupType.values().forEach { resizeCache(it, capacity) }
    fun resizeCache(type: SandboxGroupType, capacity: Long)

    fun flushCache(): CompletableFuture<*>
}
