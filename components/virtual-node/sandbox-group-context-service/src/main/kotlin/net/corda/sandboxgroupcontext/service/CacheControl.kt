package net.corda.sandboxgroupcontext.service

import net.corda.sandboxgroupcontext.SandboxGroupType
import java.util.concurrent.CompletableFuture

interface CacheControl : CacheEviction {
    fun initCaches(capacity: Long) = SandboxGroupType.values().forEach { initCache(it, capacity) }

    fun initCache(type: SandboxGroupType, capacity: Long)
    fun flushCache(): CompletableFuture<*>
}
