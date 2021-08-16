package net.corda.impl.caching.crypto

/**
 * Defines a simplified caching layer on top of an *append-only* persistence.
 * The cached and persistence items are different, the mutator defines the shape of the cached item
 * based on the persistent item.
 * It's expected that the implementation is race condition with different instances safe.
 * @param V: the cached item type
 * @param E: the persistent entity type
 */
interface SimplePersistentCache<V, E> {
    /**
     * Persist the specified value and associates mutated value of it with the specified key in this map (in that order).
     * If the underlying storage previously contained a value for the key, the behaviour is unpredictable
     * and most likely will throw an error from the underlying storage.
     */
    fun put(key: Any, entity: E, mutator: (entity: E) -> V): V

    /**
     * Returns the value associated with the key, first loading that value from the storage if necessary and mutating it
     * or null if the value is not found.
     */
    fun get(key: Any, mutator: (entity: E) -> V): V?
}

