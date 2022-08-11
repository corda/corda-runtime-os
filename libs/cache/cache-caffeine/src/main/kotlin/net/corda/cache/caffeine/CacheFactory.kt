package net.corda.cache.caffeine

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

/**
 * Caffeine cach factory that allows extra functionality to be injected to our caches.
 */
interface CacheFactory {
    fun <K, V> build(caffeine: Caffeine<in K, in V>): Cache<K, V>
}