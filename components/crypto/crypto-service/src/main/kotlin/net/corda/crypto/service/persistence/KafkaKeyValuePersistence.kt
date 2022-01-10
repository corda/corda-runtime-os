package net.corda.crypto.service.persistence

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.persistence.KeyValueMutator
import java.util.concurrent.TimeUnit

class KafkaKeyValuePersistence<V, E>(
    private val proxy: KafkaProxy<E>,
    private val memberId: String,
    config: CryptoPersistenceConfig,
    mutator: KeyValueMutator<V, E>
) : KeyValuePersistenceBase<V, E>(mutator), AutoCloseable {
    private val cache: Cache<String, V> = Caffeine.newBuilder()
        .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.maximumSize)
        .build()

    override fun put(key: String, entity: E): V {
        proxy.publish(key, entity)
        val cached = mutate(entity)
        cache.put(key, cached)
        return cached!!
    }

    override fun get(key: String): V? {
        return cache.get(key) {
            proxy.getValue(tenantId = memberId, key = it)?.let { entity -> mutate(entity) }
        }
    }

    override fun close() {
        cache.invalidateAll()
    }
}

