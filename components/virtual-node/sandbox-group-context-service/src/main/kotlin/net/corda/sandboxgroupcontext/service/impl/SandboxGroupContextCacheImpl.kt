package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor

internal class SandboxGroupContextCacheImpl(override val capacity: Long): SandboxGroupContextCache {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }

    private val contexts: Cache<VirtualNodeContext, CloseableSandboxGroupContext> = CacheFactoryImpl().build(
        "Sandbox-Cache",
        Caffeine.newBuilder()
            .maximumSize(capacity)
            .removalListener { key, value, cause ->
                logger.info("Evicting {} sandbox for: {} [{}]", key!!.sandboxGroupType, key.holdingIdentity.x500Name, cause.name)
                value?.close()
            })

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        contexts.invalidate(virtualNodeContext)
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        return contexts.get(virtualNodeContext) {
            logger.info("Caching {} sandbox for: {} (cache size: {})",
                virtualNodeContext.sandboxGroupType, virtualNodeContext.holdingIdentity.x500Name, contexts.estimatedSize())
            createFunction(virtualNodeContext)
        }
    }

    override fun close() {
        // close everything in cache
        contexts.invalidateAll()
        contexts.cleanUp()
    }
}