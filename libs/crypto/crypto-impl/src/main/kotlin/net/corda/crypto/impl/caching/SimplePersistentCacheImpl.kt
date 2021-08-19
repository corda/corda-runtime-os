package net.corda.crypto.impl.caching

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.hibernate.SessionFactory
import java.io.Serializable
import java.util.concurrent.TimeUnit

/**
 * Implements a simplified caching layer on top of an *append-only* table accessed via Hibernate mapping.
 * Note that if the same key is stored twice, typically this will result in a duplicate insert if this is racing
 * with another transaction in different instance which would be prevented by the primary key constrain.
 * The implementation is race condition with different instance safe.
 */
open class SimplePersistentCacheImpl<V, E>(
    private val entityClazz: Class<E>,
    protected val sessionFactory: SessionFactory,
    expireInMinutes: Long = 60,
    maxSize: Long = 1000
) : SimplePersistentCache<V, E>, AutoCloseable {
    protected val cache: Cache<Any, V> = Caffeine.newBuilder()
        .expireAfterAccess(expireInMinutes, TimeUnit.MINUTES)
        .maximumSize(maxSize)
        .build()

    @Suppress("NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    override fun put(key: Any, entity: E, mutator: (entity: E) -> V): V {
        sessionFactory.openSession().use { session ->
            session.transaction.begin()
            session.merge(entity)
            session.transaction.commit()
        }
        val cached = mutator(entity)
        cache.put(key, cached)
        return cached
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(key: Any, mutator: (entity: E) -> V): V? {
        if (key !is Serializable) {
            throw CryptoServiceException("The key must implement ${Serializable::class.java.name} interface.")
        }
        return cache.get(key) {
            sessionFactory.openSession().use { session ->
                session.get(entityClazz, key)
            }?.let { value ->
                mutator(value)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        try {
            sessionFactory.close()
        } catch (e: Exception) {
            // intentional
        }
    }
}

