package net.corda.crypto.service.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.persistence.IHaveMemberId
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
import java.util.concurrent.CompletableFuture

class KafkaProxy<E: IHaveMemberId>(
    private val valueClass: Class<E>,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    config: CryptoPersistenceConfig
) : AutoCloseable {
    private companion object {
        val logger: Logger = contextLogger()
        const val GROUP_NAME_KEY = "groupName"
        const val TOPIC_NAME_KEY = "topicName"
        const val CLIENT_ID_KEY = "clientId"
    }

    private val groupName: String = config.persistenceConfig.getString(GROUP_NAME_KEY)

    private val topicName: String = config.persistenceConfig.getString(TOPIC_NAME_KEY)

    private val clientId: String = config.persistenceConfig.getString(CLIENT_ID_KEY)

    private val pub: Publisher = publisherFactory.createPublisher(
        PublisherConfig(clientId)
    )

    private val sub: CompactedSubscription<String, E> = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(groupName, topicName),
        NoOpSubscriptionProcessor(valueClass)
    )

    fun publish(key: String, entity: E) : CompletableFuture<Unit> {
        logger.debug("Publishing a record '{}' with key='{}'", valueClass.name, key)
        return pub.publish(listOf(Record(topicName, key, entity)))[0]
    }

    fun getValue(memberId: String, key: String): E? {
        logger.debug("Requesting a record '{}' with key='{}' for member='{}'", valueClass.name, key, memberId)
        val value = sub.getValue(key)
        return if(value == null || value.memberId != memberId) {
            if(value != null) {
                logger.warn(
                    "The requested record '{}' with key='{}' for member='{}' is actually for '{}' member",
                    valueClass.name,
                    key,
                    memberId,
                    value.memberId
                )
            }
            null
        } else {
            value
        }
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

    override fun close() {
        pub.closeGracefully()
        sub.closeGracefully()
    }
}