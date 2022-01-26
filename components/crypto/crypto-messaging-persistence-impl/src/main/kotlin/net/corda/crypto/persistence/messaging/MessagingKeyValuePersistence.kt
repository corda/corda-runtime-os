package net.corda.crypto.persistence.messaging

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import java.util.concurrent.TimeUnit

class MessagingKeyValuePersistence<V, E>(
    private val processor: MessagingPersistenceProcessor<E>,
    expireAfterAccessMins: Long,
    maximumSize: Long,
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {
    private val cache: Cache<String, V> = Caffeine.newBuilder()
        .expireAfterAccess(expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(maximumSize)
        .build()

    override fun put(entity: E, vararg key: EntityKeyInfo): V {
        require(key.isNotEmpty()) {
            "There must be at least one key provided."
        }
        processor.publish(entity, *key).forEach { it.get() }
        val cached = mutator.mutate(entity)
        key.forEach {
            cache.put(it.key, cached)
        }
        return cached!!
    }

    override fun get(key: String): V? {
        return cache.get(key) {
            processor.getValue(it)?.let { entity -> mutator.mutate(entity) }
        }
    }

    override fun close() {
        cache.invalidateAll()
    }
}

