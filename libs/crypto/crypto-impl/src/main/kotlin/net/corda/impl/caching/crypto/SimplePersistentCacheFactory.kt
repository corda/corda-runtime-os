package net.corda.impl.caching.crypto

/**
 * Defines a factory which must create a new instance implementing [SimplePersistentCache]
 */
@FunctionalInterface
interface SimplePersistentCacheFactory<V, E> {
    fun create(): SimplePersistentCache<V, E>
}

