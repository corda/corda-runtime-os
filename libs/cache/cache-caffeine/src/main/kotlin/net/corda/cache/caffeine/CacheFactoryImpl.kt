package net.corda.cache.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import net.corda.metrics.CordaMetrics
import net.corda.utilities.concurrent.SecManagerForkJoinPool

/**
 * Caffeine [CacheFactory] that uses [SecManagerForkJoinPool].
 */
class CacheFactoryImpl: CacheFactory {
    override fun <K, V> build(name: String, caffeine: Caffeine<in K, in V>): Cache<K, V> {
        val cache: Cache<K, V> = caffeine
            .executor(SecManagerForkJoinPool.pool)
            .recordStats()
            .build()
        CaffeineCacheMetrics.monitor(CordaMetrics.registry, cache, name)
        return cache
    }
}