package net.corda.crypto.client

import net.corda.crypto.CryptoConsts
import net.corda.crypto.component.config.rpc
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.testkit.CryptoMocks
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysRequest
import net.corda.data.crypto.wire.freshkeys.WireFreshKeysResponse
import net.corda.data.crypto.wire.signing.WireSigningRequest
import net.corda.data.crypto.wire.signing.WireSigningResponse
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.test.util.createTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoLibraryClientsFactoryTests {
    private lateinit var memberId: String
    private lateinit var cryptoMocks: CryptoMocks
    private lateinit var signingServiceSender: RPCSender<WireSigningRequest, WireSigningResponse>
    private lateinit var freshKeysServiceSender: RPCSender<WireFreshKeysRequest, WireFreshKeysResponse>
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var memberIdProvider: MemberIdProvider
    private lateinit var provider: CryptoLibraryClientsFactoryProviderImpl

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        cryptoMocks = CryptoMocks()
        signingServiceSender = mock()
        freshKeysServiceSender = mock()
        publisherFactory = mock()
        memberIdProvider = mock()
        whenever(
            memberIdProvider.memberId
        ).thenReturn(memberId)
        provider = CryptoLibraryClientsFactoryProviderImpl(
            cryptoMocks.factories.cipherSuite,
            publisherFactory,
            memberIdProvider
        )
    }

    @Test
    @Timeout(30)
    fun `Should create services without starting nor providing configuration in dev mode`() {
        val factory = provider.get(
            "testComponent"
        )
        assertNotNull(factory.getFreshKeySigningService())
        assertNotNull(factory.getSigningService(CryptoConsts.CryptoCategories.LEDGER))
    }

    @Test
    @Timeout(30)
    fun `Should concurrently create production services`() {
        val config = CryptoLibraryConfigImpl(
            mapOf(
                "isDev" to "false",
                "defaultCryptoService" to emptyMap<String, Any?>(),
                "publicKeys" to emptyMap<String, Any?>(),
                "rpc" to emptyMap<String, Any?>()
            )
        )
        whenever(
            publisherFactory.createRPCSender(config.rpc.signingRpcConfig)
        ).thenReturn(signingServiceSender)
        whenever(
            publisherFactory.createRPCSender(config.rpc.freshKeysRpcConfig)
        ).thenReturn(freshKeysServiceSender)
        provider.start()
        assertTrue(provider.isRunning)
        var factory = provider.get(
            "testComponent"
        )
        assertNotNull(factory.getFreshKeySigningService())
        assertNotNull(factory.getSigningService(CryptoConsts.CryptoCategories.LEDGER))
        provider.handleConfigEvent(config)
        factory = provider.get(
            "testComponent"
        )
        (1..100).createTestCase { i ->
            if(i % 3 == 2) {
                provider.handleConfigEvent(config)
                factory = provider.get(
                    "testComponent"
                )
            }
            assertTrue(provider.isRunning)
            assertNotNull(factory.getFreshKeySigningService())
            assertNotNull(factory.getSigningService(CryptoConsts.CryptoCategories.LEDGER))
        }.runAndValidate()
        provider.stop()
        assertFalse(provider.isRunning)
    }

    @Test
    @Timeout(30)
    fun `Should concurrently create dev services`() {
        val config = CryptoLibraryConfigImpl(
            mapOf(
                "isDev" to "true",
                "defaultCryptoService" to emptyMap<String, Any?>(),
                "publicKeys" to emptyMap<String, Any?>(),
                "rpc" to emptyMap<String, Any?>()
            )
        )
        provider.start()
        var factory = provider.get(
            "testComponent"
        )
        assertNotNull(factory.getFreshKeySigningService())
        assertNotNull(factory.getSigningService(CryptoConsts.CryptoCategories.LEDGER))
        provider.handleConfigEvent(config)
        factory = provider.get(
            "testComponent"
        )
        (1..100).createTestCase { i ->
            if(i % 3 == 2) {
                provider.handleConfigEvent(config)
                factory = provider.get(
                    "testComponent"
                )
            }
            assertNotNull(factory.getFreshKeySigningService())
            assertNotNull(factory.getSigningService(CryptoConsts.CryptoCategories.LEDGER))
        }.runAndValidate()
        provider.stop()
    }
}