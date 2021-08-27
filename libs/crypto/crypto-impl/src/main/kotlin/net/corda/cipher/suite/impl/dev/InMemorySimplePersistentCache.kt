package net.corda.cipher.suite.impl.dev

import net.corda.crypto.impl.caching.SimplePersistentCache
import net.corda.crypto.impl.SigningPersistentKey
import net.corda.crypto.impl.SigningServicePersistentCache
import java.util.concurrent.ConcurrentHashMap

open class InMemorySimplePersistentCache<V, E> : SimplePersistentCache<V, E> {

    val data = ConcurrentHashMap<Any, Pair<V, E>>()

    override fun put(key: Any, entity: E, mutator: (entity: E) -> V): V {
        val value = mutator(entity)
        data[key] = Pair(value, entity)
        return value
    }

    override fun get(key: Any, mutator: (entity: E) -> V): V? = data[key]?.first
}

class InMemorySigningServicePersistentCache :
    InMemorySimplePersistentCache<SigningPersistentKey, SigningPersistentKey>(),
    SigningServicePersistentCache {
    override fun put(key: Any, entity: SigningPersistentKey): SigningPersistentKey = put(key, entity) { it }
    override fun get(key: Any): SigningPersistentKey? = get(key) { it }
    override fun findByAlias(alias: Any): SigningPersistentKey? = data.values.singleOrNull { it.first.alias == alias }?.first
}