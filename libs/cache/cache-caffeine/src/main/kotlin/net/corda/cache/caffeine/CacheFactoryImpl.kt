package net.corda.cache.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.utilities.concurrent.SecManagerForkJoinPool

/**
 * Caffeine [CacheFactory] that uses [SecManagerForkJoinPool].
 */
class CacheFactoryImpl: CacheFactory {
    override fun <K, V> build(caffeine: Caffeine<in K, in V>): Cache<K, V> {
        return caffeine
            .executor(SecManagerForkJoinPool.pool)
            .build()
    }
}