package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.IHaveTenantId
import net.corda.crypto.impl.persistence.KeyValuePersistenceBase
import net.corda.crypto.impl.persistence.KeyValueMutator
import java.util.concurrent.ConcurrentHashMap

open class InMemoryKeyValuePersistence<V: IHaveTenantId, E: IHaveTenantId>(
    val memberId: String,
    val data: ConcurrentHashMap<String, Pair<V?, E>>,
    mutator: KeyValueMutator<V, E>
) : KeyValuePersistenceBase<V, E>(mutator), AutoCloseable {

    override fun put(key: String, entity: E): V {
        val value = mutate(entity)
        data[key] = Pair(value, entity)
        return value!!
    }

    override fun get(key: String): V? {
      val value = data[key]
        return if(value == null || value.first?.tenantId != memberId) {
            null
        } else {
            value.first
        }
    }

    override fun close() {
        data.clear()
    }
}