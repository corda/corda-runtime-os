package net.corda.flow.fiber.cache.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Duration
import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.cache.FlowFiberCache

@Suppress("unused")
@Component(service = [FlowFiberCache::class])
class FlowFiberCacheImpl @Activate constructor() : FlowFiberCache {

    private companion object {
        private const val FLOW_FIBER_CACHE_MAX_SIZE_PROPERTY_NAME = "net.corda.flow.fiber.cache.maximumSize"
        private const val FLOW_FIBER_CACHE_EXPIRE_AFTER_WRITE_SECONDS_PROPERTY_NAME = "net.corda.flow.fiber.cache.expireAfterWriteSeconds"
    }

    private val maximumSize = java.lang.Long.getLong(FLOW_FIBER_CACHE_MAX_SIZE_PROPERTY_NAME, 10000)
    private val expireAfterWriteSeconds = java.lang.Long.getLong(FLOW_FIBER_CACHE_EXPIRE_AFTER_WRITE_SECONDS_PROPERTY_NAME, 600)

    private val cache: Cache<FlowKey, FlowFiberImpl> = CacheFactoryImpl().build(
        "flow-fiber-cache",
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds))
    )

    override fun put(key: FlowKey, fiber: FlowFiberImpl) {
        cache.put(key, fiber)
    }

    override fun get(key: FlowKey): FlowFiberImpl? {
        return cache.getIfPresent(key)
    }

    override fun remove(key: FlowKey) {
        cache.invalidate(key)
    }

    override fun remove(keys: List<FlowKey>) {
        cache.invalidateAll(keys)
        cache.cleanUp()
    }

    override fun remove(holdingIdentity: HoldingIdentity) {
        val keysToInvalidate = cache.asMap().keys.filter { holdingIdentity == it.identity }
        cache.invalidateAll(keysToInvalidate)
        cache.cleanUp()
    }
}