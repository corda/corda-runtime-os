package net.corda.impl.crypto

/**
 * Defines a simplified caching layer on top of an *append-only* persistence.
 * he cached and persistence items are the same.
 * It's expected that the implementation is race condition with different instances safe.
 */
interface SigningServicePersistentCache {
    /**
     * Persist the specified value and associates it with the specified key in this map (in that order).
     * If the underlying storage previously contained a value for the key, the behaviour is unpredictable
     * and most likely will throw an error from the underlying storage.
     */
    fun put(key: Any, entity: SigningPersistentKey): SigningPersistentKey

    /**
     * Returns the value associated with the key, first loading that value from the storage if necessary or null if the value is not found.
     */
    fun get(key: Any): SigningPersistentKey?

    /**
     * Returns the value associated with alias,first loading that value from the storage if necessary or null if the value is not found.
     */
    fun findByAlias(alias: Any): SigningPersistentKey?
}