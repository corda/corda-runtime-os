package net.corda.flow.fiber.cache.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.SandboxedCache
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.utilities.debug
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("unused")
@Component(service = [FlowFiberCache::class])
class FlowFiberCacheImpl @Activate constructor(
    @Reference(service = CacheEviction::class)
    private val cacheEviction: CacheEviction
) : FlowFiberCache, SandboxedCache {

    private companion object {
        private val logger = LoggerFactory.getLogger(FlowFiberCacheImpl::class.java)
        private const val FLOW_FIBER_CACHE_MAX_SIZE_PROPERTY_NAME = "net.corda.flow.fiber.cache.maximumSize"
        private const val FLOW_FIBER_CACHE_EXPIRE_AFTER_WRITE_SECONDS_PROPERTY_NAME = "net.corda.flow.fiber.cache.expireAfterWriteSeconds"
    }

    private val maximumSize = java.lang.Long.getLong(FLOW_FIBER_CACHE_MAX_SIZE_PROPERTY_NAME, 10000)
    private val expireAfterWriteSeconds = java.lang.Long.getLong(FLOW_FIBER_CACHE_EXPIRE_AFTER_WRITE_SECONDS_PROPERTY_NAME, 600)

    private data class FiberCacheValue(val fiber: FlowFiber, val suspendCount: Int)

    private val cache: Cache<FlowKey, FiberCacheValue> = CacheFactoryImpl().build(
        "flow-fiber-cache",
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds))
    )

    init {
        if (!cacheEviction.addEvictionListener(SandboxGroupType.FLOW, ::onEviction)) {
            logger.error("FAILED TO ADD EVICTION LISTENER")
        }
    }

    @Suppress("unused")
    @Deactivate
    fun shutdown() {
        if (!cacheEviction.removeEvictionListener(SandboxGroupType.FLOW, ::onEviction)) {
            logger.error("FAILED TO REMOVE EVICTION LISTENER")
        }
    }

    private fun onEviction(vnc: VirtualNodeContext) {
        logger.debug {
            "Evicting cached items from ${cache::class.java} with holding identity: ${vnc.holdingIdentity}, " +
                    "cpkFileChecksums: ${vnc.cpkFileChecksums} and sandbox type: ${SandboxGroupType.FLOW}"
        }
        remove(vnc)
    }

    override fun put(key: FlowKey, suspendCount: Int, fiber: FlowFiber) {
        cache.put(key, FiberCacheValue(fiber, suspendCount))
    }

    override fun get(key: FlowKey, suspendCount: Int): FlowFiber? {
        val fiber = cache.getIfPresent(key)
        return if(null == fiber) {
            logger.info("Fiber not found in cache: ${key.id}")
            null
        } else if (fiber.suspendCount == suspendCount) {
            logger.debug { "Fiber found in cache: ${key.id}" }
            fiber.fiber
        } else {
            logger.warn("Fiber found in cache but at wrong suspendCount (${fiber.suspendCount} <-> $suspendCount): ${key.id}")
            null
        }
    }

    override fun remove(key: FlowKey) {
        cache.invalidate(key)
    }

    override fun remove(virtualNodeContext: VirtualNodeContext) {
        logger.debug {
            "Flow fiber cache removing holdingIdentity ${virtualNodeContext.holdingIdentity}" }
        val holdingIdentityToRemove = virtualNodeContext.holdingIdentity.toAvro()
        val keysToInvalidate = cache.asMap().keys.filter { holdingIdentityToRemove == it.identity }
        cache.invalidateAll(keysToInvalidate)
        cache.cleanUp()
    }

    // Yuk ... adding this to support the existing integration test.
    //  I don't think we should have integration tests knowing about the internals of the cache.
    internal fun findInCache(holdingId: HoldingIdentity, flowId: String): FlowFiber? {
        return cache.getIfPresent(FlowKey(flowId, holdingId))?.fiber
    }
}