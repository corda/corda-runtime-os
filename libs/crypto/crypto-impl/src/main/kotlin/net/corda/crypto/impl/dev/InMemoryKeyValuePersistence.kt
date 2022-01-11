package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import java.util.concurrent.ConcurrentHashMap

open class InMemoryKeyValuePersistence<V, E>(
    private val data: ConcurrentHashMap<String, V>,
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {

    override fun put(key: String, entity: E): V {
        val value = mutator.mutate(entity)
        data[key] = value
        return value
    }

    override fun get(key: String): V? =
      data[key]

    override fun close() {
        data.clear()
    }
}