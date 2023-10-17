package net.corda.cache.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
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
        return CaffeineCacheMetrics.monitor(CordaMetrics.registry, cache, name)
    }
    override fun <K, V> buildNonAsync(name: String, caffeine: Caffeine<in K, in V>): Cache<K, V> {
        val cache: Cache<K, V> = caffeine
            .executor(Runnable::run)
            .recordStats()
            .build()
        return CaffeineCacheMetrics.monitor(CordaMetrics.registry, cache, name)
    }
    override fun <K, V> build(name: String, caffeine: Caffeine<in K, in V>, loader: CacheLoader<K, V>): LoadingCache<K, V> {
        val cache: LoadingCache<K, V> = caffeine
            .executor(SecManagerForkJoinPool.pool)
            .recordStats()
            .build(loader)
        return CaffeineCacheMetrics.monitor(CordaMetrics.registry, cache, name)
    }
}
