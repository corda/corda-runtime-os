package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.PersistentCache
import java.util.concurrent.ConcurrentHashMap

open class InMemoryPersistentCache<V: Any, E: Any>(
    val data: ConcurrentHashMap<String, Pair<V, E>> = ConcurrentHashMap<String, Pair<V, E>>()
) : PersistentCache<V, E>, AutoCloseable {
    override fun put(key: String, entity: E, mutator: (entity: E) -> V): V {
        val value = mutator(entity)
        data[key] = Pair(value, entity)
        return value
    }

    override fun get(key: String, mutator: (entity: E) -> V): V? = data[key]?.first

    override fun close() {
        data.clear()
    }
}