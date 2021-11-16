package net.corda.crypto.service.persistence

import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class KafkaKeyValuePersistenceFactoryTests {
    @Test
    @Timeout(5)
    fun `Should close publisher and subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val publisherFactory = mock<PublisherFactory>()
        val sub = mock<CompactedSubscription<String, SigningPersistentKeyInfo>>()
        val pub = mock<Publisher>()
        whenever(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, SigningPersistentKeyInfo>>(),
                any()
            )
        ).thenReturn(sub)
        whenever(
            publisherFactory.createPublisher(any(), any())
        ).thenReturn(pub)
        val factory = KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = KafkaInfrastructure.customConfig
        )
        (factory as AutoCloseable).close()
        // twice - for the signing & crypto proxies
        Mockito.verify(pub, Mockito.times(2)).close()
        Mockito.verify(sub, Mockito.times(2)).close()
    }

    @Test
    @Timeout(5)
    fun `Should create subscriptions only once`() {
        val memberId = UUID.randomUUID().toString()
        val subscriptionFactory = mock<SubscriptionFactory>()
        val publisherFactory = mock<PublisherFactory>()
        val sub = mock<CompactedSubscription<String, SigningPersistentKeyInfo>>()
        val pub = mock<Publisher>()
        whenever(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<*, *>>(),
                any()
            )
        ).thenReturn(sub)
        whenever(
            publisherFactory.createPublisher(any(), any())
        ).thenReturn(pub)
        val factory = KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = KafkaInfrastructure.customConfig
        )
        factory.createSigningPersistence(memberId) { it }
        factory.createDefaultCryptoPersistence(UUID.randomUUID().toString()) {
            DefaultCryptoCachedKeyInfo(memberId)
        }
        factory.createSigningPersistence(memberId) { it }
        factory.createDefaultCryptoPersistence(UUID.randomUUID().toString()) {
            DefaultCryptoCachedKeyInfo(memberId)
        }
        factory.createSigningPersistence(memberId) { it }
        factory.createDefaultCryptoPersistence(UUID.randomUUID().toString()) {
            DefaultCryptoCachedKeyInfo(memberId)
        }
        (factory as AutoCloseable).close()
        // twice - for the signing & crypto proxies, and that's it
        Mockito.verify(pub, Mockito.times(2)).close()
        Mockito.verify(sub, Mockito.times(2)).close()
    }
}