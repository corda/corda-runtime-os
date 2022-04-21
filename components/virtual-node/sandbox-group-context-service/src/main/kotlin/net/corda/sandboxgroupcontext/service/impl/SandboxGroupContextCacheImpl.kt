package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor

internal class SandboxGroupContextCacheImpl(override val cacheSize: Long): SandboxGroupContextCache {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }
    private val contexts: Cache<VirtualNodeContext, CloseableSandboxGroupContext> = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .removalListener<VirtualNodeContext, CloseableSandboxGroupContext> { key, value, cause ->
            logger.info("Evicting ${key!!.sandboxGroupType} sandbox for: ${key.holdingIdentity.x500Name} [${cause.name}]")
            value?.close()
        }
        .build()

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        contexts.invalidate(virtualNodeContext)
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        return contexts.get(virtualNodeContext) {
            logger.info("Caching ${virtualNodeContext.sandboxGroupType} sandbox for: " +
                    "${virtualNodeContext.holdingIdentity.x500Name} (cache size: ${contexts.estimatedSize()}")
            createFunction(virtualNodeContext)
        }
    }

    override fun close() {
        // close everything in cache
        contexts.invalidateAll()
        contexts.cleanUp()
    }
}