package net.corda.crypto.service

import net.corda.crypto.CryptoCategories
import net.corda.crypto.impl.DefaultCryptoServiceProvider
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.service.persistence.KafkaPersistentCacheFactoryImpl
import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
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
            KafkaPersistentCacheFactoryImpl(
                mock(),
                mock()
            ),
            cryptoMocks.persistentCacheFactory
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
                    "cacheFactoryName" to "dev"
                ),
                "mngCache" to mapOf(
                    "cacheFactoryName" to "dev"
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
        val latch = CountDownLatch(1)
        val threads = mutableListOf<Thread>()
        for (i in 1..100) {
            val thread = thread(start = true) {
                latch.await(20, TimeUnit.SECONDS)
                factory.handleConfigEvent(config)
                assertTrue(factory.isRunning)
                assertNotNull(factory.getSigningService(UUID.randomUUID().toString(), CryptoCategories.LEDGER))
                assertNotNull(factory.getFreshKeySigningService(UUID.randomUUID().toString()))
            }
            threads.add(thread)
        }
        latch.countDown()
        threads.forEach {
            it.join(5_000)
        }
        factory.stop()
        assertFalse(factory.isRunning)
    }
}