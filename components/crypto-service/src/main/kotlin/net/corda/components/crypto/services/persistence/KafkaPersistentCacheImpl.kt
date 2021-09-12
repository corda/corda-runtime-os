package net.corda.components.crypto.services.persistence

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.components.crypto.closeGracefully
import net.corda.components.crypto.config.CryptoCacheConfig
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

/**
 * Implements a simplified caching layer on top of an *append-only* table accessed via Hibernate mapping.
 * Note that if the same key is stored twice, typically this will result in a duplicate insert if this is racing
 * with another transaction in different instance which would be prevented by the primary key constrain.
 * The implementation is race condition with different instance safe.
 */
open class KafkaPersistentCacheImpl<V: Any, E: Any>(
    private val entityClazz: Class<E>,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    config: CryptoCacheConfig
) : PersistentCache<V, E>, AutoCloseable {
    private companion object {
        val logger: Logger = contextLogger()
        const val groupNameKey = "groupName"
        const val topicNameKey = "topicName"
    }

    private val groupName: String = config.persistenceConfig.getString(groupNameKey)

    private val topicName: String = config.persistenceConfig.getString(topicNameKey)

    private val cache: Cache<String, V> = Caffeine.newBuilder()
        .expireAfterAccess(config.expireAfterAccessMins, TimeUnit.MINUTES)
        .maximumSize(config.maximumSize)
        .build()

    private val pub: Publisher = publisherFactory.createPublisher(
        PublisherConfig("")
    )
    private val sub: CompactedSubscription<String, E> = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(groupName, topicName),
        NoOpSubscriptionProcessor(entityClazz)
    )

    @Suppress("NULLABLE_TYPE_PARAMETER_AGAINST_NOT_NULL_TYPE_PARAMETER")
    override fun put(key: String, entity: E, mutator: (entity: E) -> V): V {
        logger.debug("Storing {} with key {}", entityClazz::class.qualifiedName, key)
        pub.publish(listOf(
            Record(topicName, key, entity)
        ))
        val cached = mutator(entity)
        cache.put(key, cached)
        return cached
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(key: String, mutator: (entity: E) -> V): V? {
        logger.debug("Retrieving {} for key {}", entityClazz::class.qualifiedName, key)
        return cache.get(key) {
            sub.getValue(it)?.let { value ->
                mutator(value)
            }
        }
    }

    override fun close() {
        cache.invalidateAll()
        sub.closeGracefully()
        pub.closeGracefully()
    }

    private class NoOpSubscriptionProcessor<E: Any>(
        override val valueClass: Class<E>
    ) : CompactedProcessor<String, E> {
        override val keyClass: Class<String>
            get() = String::class.java
        override fun onSnapshot(currentData: Map<String, E>) {
        }
        override fun onNext(newRecord: Record<String, E>, oldValue: E?, currentData: Map<String, E>) {
        }
    }
}

