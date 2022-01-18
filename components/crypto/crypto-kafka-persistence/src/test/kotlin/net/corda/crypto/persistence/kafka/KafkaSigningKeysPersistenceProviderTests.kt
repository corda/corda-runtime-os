package net.corda.crypto.persistence.kafka

import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.createTestCase
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KafkaSigningKeysPersistenceProviderTests {
    private lateinit var sub: CompactedSubscription<String, SigningKeysRecord>
    private lateinit var pub: Publisher
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var config: CryptoLibraryConfig
    private lateinit var provider: KafkaSigningKeysPersistenceProvider

    @BeforeEach
    fun setup() {
        sub = mock()
        pub = mock()
        subscriptionFactory = mock  {
            on {
                createCompactedSubscription(any(), any<CompactedProcessor<*, *>>(), any())
            }.thenReturn(sub)
        }
        publisherFactory = mock {
            on {
                createPublisher(any(), any())
            }.thenReturn(pub)
        }
        config = KafkaInfrastructure.CryptoLibraryConfigTestImpl(emptyMap())
        provider = KafkaSigningKeysPersistenceProvider()
        provider.publisherFactory = publisherFactory
        provider.subscriptionFactory = subscriptionFactory
    }

    @Test
    @Timeout(30)
    fun `Should return instances using same processor instance until config is changed regardless of tenant`() {
        provider.start()
        assertTrue(provider.isRunning)
        provider.handleConfigEvent(config)
        var expectedCount = 1
        assertNotNull(provider.getInstance(UUID.randomUUID().toString()) { it })
        for (i in 1..100) {
            if(i % 3 == 2) {
                provider.handleConfigEvent(config)
                expectedCount ++
            }
            assertNotNull(provider.getInstance(UUID.randomUUID().toString()) { it })
            assertTrue(provider.isRunning)
        }
        provider.stop()
        assertFalse(provider.isRunning)
        Mockito.verify(pub, Mockito.times(expectedCount)).close()
        Mockito.verify(sub, Mockito.times(expectedCount)).close()
        Mockito.verify(subscriptionFactory, times(expectedCount))
            .createCompactedSubscription(any(), any<CompactedProcessor<*, *>>(), any())
        Mockito.verify(publisherFactory, times(expectedCount))
            .createPublisher(any(), any())
    }

    @Test
    @Timeout(30)
    fun `Should concurrently return instances regardless of tenant`() {
        provider.start()
        assertTrue(provider.isRunning)
        provider.handleConfigEvent(config)
        assertNotNull(provider.getInstance(UUID.randomUUID().toString()) { it })
        (1..100).createTestCase {
            assertNotNull(provider.getInstance(UUID.randomUUID().toString()) { it })
            assertTrue(provider.isRunning)
        }.runAndValidate()
        provider.stop()
        assertFalse(provider.isRunning)
        Mockito.verify(pub, Mockito.times(1)).close()
        Mockito.verify(sub, Mockito.times(1)).close()
        Mockito.verify(subscriptionFactory, times(1))
            .createCompactedSubscription(any(), any<CompactedProcessor<*, *>>(), any())
        Mockito.verify(publisherFactory, times(1))
            .createPublisher(any(), any())
    }

    @Test
    @Timeout(30)
    fun `Should throw IllegalStateException if provider is not started yet`() {
        assertFalse(provider.isRunning)
        assertThrows<IllegalStateException> {
            provider.handleConfigEvent(config)
        }
    }

    @Test
    @Timeout(30)
    fun `Should throw IllegalStateException if config is not received yet`() {
        provider.start()
        assertTrue(provider.isRunning)
        assertThrows<IllegalStateException> {
            provider.getInstance(UUID.randomUUID().toString()) { it }
        }
    }
}