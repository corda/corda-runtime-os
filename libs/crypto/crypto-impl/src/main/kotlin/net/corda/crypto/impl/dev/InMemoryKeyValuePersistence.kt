package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.mutateOrNull
import java.util.concurrent.ConcurrentHashMap

open class InMemoryKeyValuePersistence<V, E>(
    val data: ConcurrentHashMap<String, Pair<V?, E>>,
    val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {

    override fun put(key: String, entity: E): V {
        val value = mutator.mutateOrNull(entity)
        data[key] = Pair(value, entity)
        return value!!
    }

    override fun get(key: String): V? =
      data[key]?.first

    override fun close() {
        data.clear()
    }
}