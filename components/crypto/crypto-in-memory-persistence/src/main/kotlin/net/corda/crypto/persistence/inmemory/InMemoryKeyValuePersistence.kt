package net.corda.crypto.persistence.inmemory

import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import java.util.concurrent.ConcurrentHashMap

class InMemoryKeyValuePersistence<V, E>(
    private val data: ConcurrentHashMap<String, V>,
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {

    override fun put(entity: E, vararg key: EntityKeyInfo): V {
        require(key.isNotEmpty()) {
            "There must be at least one key provided."
        }
        val value = mutator.mutate(entity)
        key.forEach {
            data[it.key] = value
        }
        return value
    }

    override fun get(key: String): V? =
        data[key]

    override fun close() {
        data.clear()
    }
}