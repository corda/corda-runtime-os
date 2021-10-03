package net.corda.crypto.impl

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CipherSuiteFactoryTests {
    private lateinit var schemeMetadata: List<CipherSchemeMetadata>
    private lateinit var schemeMetadataProviders: List<CipherSchemeMetadataProvider>
    private lateinit var verifiers: List<SignatureVerificationService>
    private lateinit var verifierProviders: List<SignatureVerificationServiceProvider>
    private lateinit var digestServices: List<DigestService>
    private lateinit var digestServiceProviders: List<DigestServiceProvider>
    private lateinit var factory: CipherSuiteFactoryImpl

    @BeforeEach
    fun setup() {
        schemeMetadata = listOf(
            mock(),
            mock(),
            mock()
        )
        schemeMetadataProviders = listOf(
            mock(),
            mock(),
            mock()
        )
        whenever(
            schemeMetadataProviders[0].name
        ).thenReturn("p0")
        whenever(
            schemeMetadataProviders[0].getInstance()
        ).thenReturn(schemeMetadata[0])
        whenever(
            schemeMetadataProviders[1].name
        ).thenReturn("p1")
        whenever(
            schemeMetadataProviders[1].getInstance()
        ).thenReturn(schemeMetadata[1])
        whenever(
            schemeMetadataProviders[2].name
        ).thenReturn("default")
        whenever(
            schemeMetadataProviders[2].getInstance()
        ).thenReturn(schemeMetadata[2])
        verifiers = listOf(
            mock(),
            mock(),
            mock()
        )
        verifierProviders = listOf(
            mock(),
            mock(),
            mock()
        )
        whenever(
            verifierProviders[0].name
        ).thenReturn("p0")
        whenever(
            verifierProviders[0].getInstance(any())
        ).thenReturn(verifiers[0])
        whenever(
            verifierProviders[1].name
        ).thenReturn("p1")
        whenever(
            verifierProviders[1].getInstance(any())
        ).thenReturn(verifiers[1])
        whenever(
            verifierProviders[2].name
        ).thenReturn("default")
        whenever(
            verifierProviders[2].getInstance(any())
        ).thenReturn(verifiers[2])
        digestServices = listOf(
            mock(),
            mock(),
            mock()
        )
        digestServiceProviders = listOf(
            mock(),
            mock(),
            mock()
        )
        whenever(
            digestServiceProviders[0].name
        ).thenReturn("p0")
        whenever(
            digestServiceProviders[0].getInstance(any())
        ).thenReturn(digestServices[0])
        whenever(
            digestServiceProviders[1].name
        ).thenReturn("p1")
        whenever(
            digestServiceProviders[1].getInstance(any())
        ).thenReturn(digestServices[1])
        whenever(
            digestServiceProviders[2].name
        ).thenReturn("default")
        whenever(
            digestServiceProviders[2].getInstance(any())
        ).thenReturn(digestServices[2])
        factory = CipherSuiteFactoryImpl(
            schemeMetadataProviders,
            verifierProviders,
            digestServiceProviders
        )
    }

    @Test
    @Timeout(30)
    fun `Should create services without starting nor providing configuration`() {
        assertSame(schemeMetadata[2], factory.getSchemeMap())
        assertSame(verifiers[2], factory.getSignatureVerificationService())
        assertSame(digestServices[2], factory.getDigestService())
    }

    @Test
    @Timeout(30)
    fun `Should concurrently create services`() {
        factory.start()
        assertTrue(factory.isRunning)
        val latch = CountDownLatch(1)
        val threads = mutableListOf<Thread>()
        for (i in 1..100) {
            val thread = thread(start = true) {
                latch.await(20, TimeUnit.SECONDS)
                val expected: Int
                val config = when(i % 3) {
                    1 -> {
                        expected = 0
                        CryptoLibraryConfigImpl(
                            mapOf(
                                "isDev" to "false",
                                "keyCache" to emptyMap<String, Any?>(),
                                "mngCache" to emptyMap<String, Any?>(),
                                "rpc" to emptyMap<String, Any?>(),
                                "cipherSuite" to mapOf(
                                    "schemeMetadataProvider" to "p0",
                                    "signatureVerificationProvider" to "p0",
                                    "digestProvider" to "p0"
                                )
                            )
                        )
                    }
                    2 -> {
                        expected = 1
                        CryptoLibraryConfigImpl(
                            mapOf(
                                "isDev" to "false",
                                "keyCache" to emptyMap<String, Any?>(),
                                "mngCache" to emptyMap<String, Any?>(),
                                "rpc" to emptyMap<String, Any?>(),
                                "cipherSuite" to mapOf(
                                    "schemeMetadataProvider" to "p1",
                                    "signatureVerificationProvider" to "p1",
                                    "digestProvider" to "p1"
                                )
                            )
                        )
                    }
                    else -> {
                        expected = 2
                        CryptoLibraryConfigImpl(
                            mapOf(
                                "isDev" to "false",
                                "keyCache" to emptyMap<String, Any?>(),
                                "mngCache" to emptyMap<String, Any?>(),
                                "rpc" to emptyMap<String, Any?>()
                            )
                        )
                    }
                }
                factory.handleConfigEvent(config)
                assertTrue(factory.isRunning)
                assertSame(schemeMetadata[expected], factory.getSchemeMap())
                assertSame(verifiers[expected], factory.getSignatureVerificationService())
                assertSame(digestServices[expected], factory.getDigestService())
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