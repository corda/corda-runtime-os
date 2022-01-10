package net.corda.crypto.service.persistence

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.config.DefaultConfigConsts
import net.corda.crypto.impl.persistence.SigningKeyRecord
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KafkaKeyValuePersistenceFactoryProviderTests {
    companion object {
        const val KEY_CACHE_TOPIC_NAME = "keyCacheTopic"
        const val MNG_CACHE_TOPIC_NAME = "mngCacheTopic"
        const val GROUP_NAME = "groupName"
        const val CLIENT_ID = "clientId"
    }

    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var config: CryptoLibraryConfig
    private lateinit var provider: KafkaKeyValuePersistenceFactoryProvider

    @BeforeEach
    fun setup() {
        subscriptionFactory = mock()
        publisherFactory = mock()
        val sub = mock<CompactedSubscription<String, SigningKeyRecord>>()
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
        config = CryptoLibraryConfigImpl(
            mapOf(
                "defaultCryptoService" to mapOf(
                    "persistenceConfig" to mapOf(
                        DefaultConfigConsts.Kafka.GROUP_NAME_KEY to GROUP_NAME,
                        DefaultConfigConsts.Kafka.TOPIC_NAME_KEY to KEY_CACHE_TOPIC_NAME,
                        DefaultConfigConsts.Kafka.CLIENT_ID_KEY to CLIENT_ID
                    )
                ),
                "publicKeys" to mapOf(
                    "persistenceConfig" to mapOf(
                        DefaultConfigConsts.Kafka.GROUP_NAME_KEY to GROUP_NAME,
                        DefaultConfigConsts.Kafka.TOPIC_NAME_KEY to MNG_CACHE_TOPIC_NAME,
                        DefaultConfigConsts.Kafka.CLIENT_ID_KEY to CLIENT_ID
                    )

                )
            )
        )
        provider = KafkaKeyValuePersistenceFactoryProvider(
            subscriptionFactory,
            publisherFactory
        )
    }

    @Test
    @Timeout(30)
    fun `Should concurrently return same factory instance until conig is changed`() {
        provider.start()
        assertTrue(provider.isRunning)
        provider.handleConfigEvent(config)
        assertNotNull(provider.get())
        (1..100).createTestCase { i ->
            if(i % 3 == 2) {
                provider.handleConfigEvent(config)
            }
            assertNotNull(provider.get())
            assertTrue(provider.isRunning)
        }.runAndValidate()
        provider.stop()
        assertFalse(provider.isRunning)
    }

    @Test
    @Timeout(30)
    fun `Should return same factory instance until config is changed`() {
        provider.start()
        assertTrue(provider.isRunning)
        provider.handleConfigEvent(config)
        val original = provider.get()
        assertNotNull(original)
        assertSame(original, provider.get())
        assertSame(original, provider.get())
        assertSame(original, provider.get())
        provider.handleConfigEvent(config)
        assertNotSame(original, provider.get())
        provider.stop()
        assertFalse(provider.isRunning)
    }
}