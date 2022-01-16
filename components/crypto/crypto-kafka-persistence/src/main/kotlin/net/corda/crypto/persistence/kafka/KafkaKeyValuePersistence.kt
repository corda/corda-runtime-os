package net.corda.crypto.persistence.kafka

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import java.util.concurrent.TimeUnit

class KafkaKeyValuePersistence<V, E>(
    private val proxy: KafkaPersistenceProxy<E>,
    private val tenantId: String,
    expireAfterAccessMins: Long,
    maximumSize: Long,
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {
    private val cache: Cache<String, V> = Caffeine.newBuilder()
        .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(maximumSize)
        .build()

    override fun put(key: String, entity: E): V {
        proxy.publish(key, entity)
        val cached = mutator.mutate(entity)
        cache.put(key, cached)
        return cached!!
    }

    override fun get(key: String): V? {
        return cache.get(key) {
            proxy.getValue(tenantId = tenantId, key = it)?.let { entity -> mutator.mutate(entity) }
        }
    }

    override fun close() {
        cache.invalidateAll()
    }
}

