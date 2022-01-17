package net.corda.crypto.persistence.kafka

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import java.util.concurrent.TimeUnit

class KafkaKeyValuePersistence<V, E>(
    private val processor: KafkaPersistenceProcessor<E>,
    private val tenantId: String,
    expireAfterAccessMins: Long,
    maximumSize: Long,
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {
    private val cache: Cache<String, V> = Caffeine.newBuilder()
        .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(maximumSize)
        .build()

    override fun put(entity: E, vararg key: EntityKeyInfo): V {
        processor.publish(entity, *key).forEach { it.get() }
        val cached = mutator.mutate(entity)
        key.forEach {
            cache.put(it.key, cached)
        }
        return cached!!
    }

    override fun get(key: String): V? {
        return cache.get(key) {
            processor.getValue(tenantId = tenantId, key = it)?.let { entity -> mutator.mutate(entity) }
        }
    }

    override fun close() {
        cache.invalidateAll()
    }
}

