package net.corda.sandboxgroupcontext.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.base.util.loggerFor
import java.util.concurrent.ConcurrentHashMap

internal class SandboxGroupContextCacheImpl(override val capacity: Long): SandboxGroupContextCache {
    private companion object {
        private val logger = loggerFor<SandboxGroupContextCache>()
    }

    private val contexts = ConcurrentHashMap(SandboxGroupType.values().associate {
            val cache: Cache<VirtualNodeContext, CloseableSandboxGroupContext> = CacheFactoryImpl().build(
                "Sandbox-Cache-${it.name}",
                Caffeine.newBuilder()
                    .maximumSize(capacity)
                    .removalListener { key, value, cause ->
                        logger.info(
                            "Evicting {} sandbox for: {} [{}]",
                            key!!.sandboxGroupType,
                            key.holdingIdentity.x500Name,
                            cause.name
                        )
                        value?.close()
                    })
            Pair(it.name, cache)
        })

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        contexts.computeIfPresent(virtualNodeContext.sandboxGroupType.name) { _, cache ->
            cache.invalidate(virtualNodeContext)
            cache
        }
    }

    override fun get(
        virtualNodeContext: VirtualNodeContext,
        createFunction: (VirtualNodeContext) -> CloseableSandboxGroupContext
    ): SandboxGroupContext {
        val cache = contexts[virtualNodeContext.sandboxGroupType.name] ?: throw IllegalArgumentException("Sandbox of type ${virtualNodeContext.sandboxGroupType.name} does not exist.")
        return cache.get(virtualNodeContext) {
            logger.info("Caching {} sandbox for: {} (cache size: {})",
                virtualNodeContext.sandboxGroupType, virtualNodeContext.holdingIdentity.x500Name, cache.estimatedSize())
            createFunction(virtualNodeContext)
        }
    }

    override fun close() {
        contexts.values.forEach {
            // close everything in cache
            it.invalidateAll()
            it.cleanUp()
        }
    }
}