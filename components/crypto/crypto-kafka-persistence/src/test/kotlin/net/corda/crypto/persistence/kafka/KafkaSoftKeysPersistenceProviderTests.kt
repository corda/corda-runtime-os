package net.corda.crypto.persistence.kafka

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.persistence.CachedSoftKeysRecord
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.createTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KafkaSoftKeysPersistenceProviderTests {
    private lateinit var sub: CompactedSubscription<String, SoftKeysRecord>
    private lateinit var pub: Publisher
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var config: SmartConfig
    private lateinit var provider: KafkaSoftKeysPersistenceProvider
    private lateinit var coordinator: LifecycleCoordinator
    private lateinit var registrationHandle: AutoCloseable
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var configurationReadService: ConfigurationReadService

    @BeforeEach
    fun setup() {
        coordinator = mock()
        registrationHandle = mock()
        coordinatorFactory = mock {
            on { createCoordinator(any(), any()) } doReturn coordinator
        }
        configurationReadService = mock {
            on { registerForUpdates(any()) } doReturn registrationHandle
        }
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
        config = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        provider = KafkaSoftKeysPersistenceProvider(
            coordinatorFactory,
            subscriptionFactory,
            publisherFactory,
            configurationReadService
        ).also {
            it.start()
            coordinator.postEvent(
                RegistrationStatusChangeEvent(
                    registration = mock(),
                    status = LifecycleStatus.UP
                )
            )
        }
    }

    @Test
    @Timeout(30)
    fun `Should return instances using same processor instance until config is changed regardless of tenant`() {
        provider.start()
        assertTrue(provider.isRunning)
        coordinator.postEvent(NewConfigurationReceivedEvent(config))
        var expectedCount = 1
        assertNotNull(
            provider.getInstance(UUID.randomUUID().toString()) {
                CachedSoftKeysRecord(tenantId = it.tenantId)
            }
        )
        for (i in 1..100) {
            if(i % 3 == 2) {
                coordinator.postEvent(NewConfigurationReceivedEvent(config))
                expectedCount ++
            }
            assertNotNull(
                provider.getInstance(UUID.randomUUID().toString()) {
                    CachedSoftKeysRecord(tenantId = it.tenantId)
                }
            )
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
        coordinator.postEvent(NewConfigurationReceivedEvent(config))
        assertNotNull(
            provider.getInstance(UUID.randomUUID().toString()) {
                CachedSoftKeysRecord(tenantId = it.tenantId)
            }
        )
        (1..100).createTestCase {
            assertNotNull(
                provider.getInstance(UUID.randomUUID().toString()) {
                    CachedSoftKeysRecord(tenantId = it.tenantId)
                }
            )
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
            coordinator.postEvent(NewConfigurationReceivedEvent(config))
        }
    }

    @Test
    @Timeout(30)
    fun `Should throw IllegalStateException if config is not received yet`() {
        provider.start()
        assertTrue(provider.isRunning)
        assertThrows<IllegalStateException> {
            provider.getInstance(UUID.randomUUID().toString()) {
                CachedSoftKeysRecord(tenantId = it.tenantId)
            }
        }
    }
}