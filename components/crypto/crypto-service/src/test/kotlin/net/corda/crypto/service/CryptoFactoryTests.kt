package net.corda.crypto.service

import net.corda.crypto.CryptoCategories
import net.corda.crypto.impl.DefaultCryptoServiceProvider
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.dev.InMemoryKeyValuePersistenceFactoryProvider
import net.corda.crypto.service.persistence.KafkaKeyValuePersistenceFactoryProvider
import net.corda.crypto.testkit.CryptoMocks
import net.corda.test.util.createTestCase
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoFactoryTests {
    private lateinit var cryptoMocks: CryptoMocks
    private lateinit var factory: CryptoFactoryImpl
    private lateinit var config: CryptoLibraryConfig

    @BeforeEach
    fun setup() {
        cryptoMocks = CryptoMocks()
        val persistenceProviders = listOf(
            KafkaKeyValuePersistenceFactoryProvider(
                mock(),
                mock()
            ),
            cryptoMocks.persistenceFactoryProvider
        )
        val defaultCryptoServiceProvider = DefaultCryptoServiceProvider(
            persistenceProviders
        )
        factory = CryptoFactoryImpl(
            persistenceProviders,
            cipherSuiteFactory = cryptoMocks.factories.cipherSuite,
            cryptoServiceProviders = listOf(
                defaultCryptoServiceProvider
            )
        )
        config = CryptoLibraryConfigImpl(
            mapOf(
                "keyCache" to mapOf(
                    "factoryName" to InMemoryKeyValuePersistenceFactoryProvider.NAME
                ),
                "mngCache" to mapOf(
                    "factoryName" to InMemoryKeyValuePersistenceFactoryProvider.NAME
                )
            )
        )
        defaultCryptoServiceProvider.handleConfigEvent(config)
        factory.handleConfigEvent(config)
    }

    @Test
    @Timeout(30)
    fun `Should create services without starting`() {
        assertNotNull(factory.getSigningService(UUID.randomUUID().toString(), CryptoCategories.LEDGER))
        assertNotNull(factory.getFreshKeySigningService(UUID.randomUUID().toString()))
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instances concurrently`() {
        factory.start()
        assertTrue(factory.isRunning)
        (1..100).createTestCase {
            factory.handleConfigEvent(config)
            assertTrue(factory.isRunning)
            assertNotNull(factory.getSigningService(UUID.randomUUID().toString(), CryptoCategories.LEDGER))
            assertNotNull(factory.getFreshKeySigningService(UUID.randomUUID().toString()))
        }.runAndValidate()
        factory.stop()
        assertFalse(factory.isRunning)
    }
}