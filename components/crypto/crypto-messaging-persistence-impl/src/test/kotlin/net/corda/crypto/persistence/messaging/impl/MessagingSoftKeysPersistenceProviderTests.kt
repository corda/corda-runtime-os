package net.corda.crypto.persistence.messaging.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.persistence.CachedSoftKeysRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.test.util.createTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MessagingSoftKeysPersistenceProviderTests : ProviderTestsBase<MessagingSoftKeysPersistenceProvider>() {
    @BeforeEach
    fun setup() {
        setup {
            MessagingSoftKeysPersistenceProvider(
                coordinatorFactory,
                subscriptionFactory,
                publisherFactory,
                configurationReadService
            )
        }
        assertFalse(provider.isRunning)
        start()
        postUpEvent()
        assertTrue(provider.isRunning)    }

    @Test
    @Timeout(30)
    fun `Should return instances using same processor instance until config is changed regardless of tenant`() {
        coordinator.postEvent(
            ConfigChangedEvent(
                setOf(CRYPTO_CONFIG, BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    CRYPTO_CONFIG to emptyConfig,
                    BOOT_CONFIG to emptyConfig,
                    MESSAGING_CONFIG to emptyConfig
                )
            )
        )
        var expectedCount = 1
        assertNotNull(
            provider.getInstance(UUID.randomUUID().toString()) {
                CachedSoftKeysRecord(tenantId = it.tenantId)
            }
        )
        for (i in 1..100) {
            if(i % 3 == 2) {
                coordinator.postEvent(
                    ConfigChangedEvent(
                        setOf(CRYPTO_CONFIG, BOOT_CONFIG, MESSAGING_CONFIG),
                        mapOf(
                            CRYPTO_CONFIG to emptyConfig,
                            BOOT_CONFIG to emptyConfig,
                            MESSAGING_CONFIG to emptyConfig
                        )
                    )
                )
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
        coordinator.postEvent(
            ConfigChangedEvent(
                setOf(CRYPTO_CONFIG, BOOT_CONFIG, MESSAGING_CONFIG),
                mapOf(
                    CRYPTO_CONFIG to emptyConfig,
                    BOOT_CONFIG to emptyConfig,
                    MESSAGING_CONFIG to emptyConfig
                )
            )
        )
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