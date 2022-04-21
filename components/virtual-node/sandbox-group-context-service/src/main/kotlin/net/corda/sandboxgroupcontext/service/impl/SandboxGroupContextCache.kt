package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor

class SandboxGroupContextCache(cacheSize: Long = 25) {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }
    private val contexts: Cache<VirtualNodeContext, CloseableSandboxGroupContext> = Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .evictionListener<VirtualNodeContext, CloseableSandboxGroupContext> { key, value, cause ->
            logger.info("Evicting ${key!!.sandboxGroupType} sandbox for: ${key.holdingIdentity.x500Name} [${cause.name}]")
            value?.close()
        }
        .build()

    fun remove(virtualNodeContext: VirtualNodeContext) {
        contexts.asMap().remove(virtualNodeContext)?.close()
    }

    fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        return contexts.get(virtualNodeContext) {
            logger.info("Caching ${virtualNodeContext.sandboxGroupType} sandbox for: ${virtualNodeContext.holdingIdentity.x500Name} (cache size: ${contexts.estimatedSize()}")
            createFunction(virtualNodeContext)
        }
    }

    fun close() {
        // close everything in cache
        contexts.asMap().forEach { (k, closeable) ->
            try {
                closeable.close()
            } catch(e: Exception) {
                logger.warn("Failed to close '$k' SandboxGroupContext", e)
            }
        }
        contexts.invalidateAll()
    }
}

