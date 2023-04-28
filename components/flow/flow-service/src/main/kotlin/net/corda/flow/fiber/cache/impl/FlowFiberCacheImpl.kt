package net.corda.flow.fiber.cache.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Duration
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.fiber.cache.FlowFiberCacheKey
import net.corda.virtualnode.HoldingIdentity

@Suppress("unused")
@Component(service = [FlowFiberCache::class])
class FlowFiberCacheImpl @Activate constructor() : FlowFiberCache {

    private val maximumSize = java.lang.Long.getLong("net.corda.flow.fiber.cache.maximumSize", 10000)
    private val expireAfterWriteSeconds = java.lang.Long.getLong("net.corda.flow.fiber.cache.expireAfterWriteSeconds", 600)

    private val cache: Cache<FlowFiberCacheKey, FlowFiberImpl> = CacheFactoryImpl().build(
        "flow-fiber-cache",
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds))
    )

    override fun put(key: FlowFiberCacheKey, fiber: FlowFiberImpl) {
        cache.put(key, fiber)
    }

    override fun get(key: FlowFiberCacheKey): FlowFiberImpl? {
        return cache.getIfPresent(key)
    }

    override fun remove(key: FlowFiberCacheKey) {
        cache.invalidate(key)
    }

    override fun remove(holdingIdentities: Set<HoldingIdentity>) {
        val keysToInvalidate = cache.asMap().keys.filter { holdingIdentities.contains(it.holdingIdentity) }
        cache.invalidateAll(keysToInvalidate)
        cache.cleanUp()
    }
}