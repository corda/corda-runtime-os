package net.corda.flow.fiber

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Duration

@Suppress("unused")
@Component(service = [FlowFiberCache::class])
class FlowFiberCacheImpl @Activate constructor() : FlowFiberCache {

    private val maximumSize = java.lang.Long.getLong("net.corda.flow.fiber.cache.maximumSize", 10)
    private val expireAfterWriteSeconds = java.lang.Long.getLong("net.corda.flow.fiber.cache.expireAfterWriteSeconds", 60)

    private val cache: Cache<String, Any> = CacheFactoryImpl().build(
        "flow-fiber-cache",
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(Duration.ofSeconds(expireAfterWriteSeconds))
    )

    override fun put(flowId: String, fiber: Any) {
        cache.put(flowId, fiber)
    }

    override fun get(flowId: String): Any? {
        return cache.getIfPresent(flowId)
    }

    override fun remove(flowId: String) {
        cache.invalidate(flowId)
    }
}