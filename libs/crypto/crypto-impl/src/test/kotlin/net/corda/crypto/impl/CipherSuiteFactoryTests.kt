package net.corda.crypto.impl

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.test.util.createTestCase
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
            object : CipherSchemeMetadataProvider {
                override val name: String = "p0"
                override fun getInstance(): CipherSchemeMetadata = schemeMetadata[0]
            },
            object : CipherSchemeMetadataProvider {
                override val name: String = "p1"
                override fun getInstance(): CipherSchemeMetadata = schemeMetadata[1]
            },
            object : CipherSchemeMetadataProvider {
                override val name: String = "default"
                override fun getInstance(): CipherSchemeMetadata = schemeMetadata[2]
            }
        )
        verifiers = listOf(
            mock(),
            mock(),
            mock()
        )
        verifierProviders = listOf(
            object : SignatureVerificationServiceProvider {
                override val name: String = "p0"
                override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): SignatureVerificationService =
                    verifiers[0]
            },
            object : SignatureVerificationServiceProvider {
                override val name: String = "p1"
                override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): SignatureVerificationService =
                    verifiers[1]
            },
            object : SignatureVerificationServiceProvider {
                override val name: String = "default"
                override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): SignatureVerificationService =
                    verifiers[2]
            }
        )
        digestServices = listOf(
            mock(),
            mock(),
            mock()
        )
        digestServiceProviders = listOf(
            object : DigestServiceProvider {
                override val name: String = "p0"
                override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService = digestServices[0]
            },
            object : DigestServiceProvider {
                override val name: String = "p1"
                override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService = digestServices[1]
            },
            object : DigestServiceProvider {
                override val name: String = "default"
                override fun getInstance(cipherSuiteFactory: CipherSuiteFactory): DigestService = digestServices[2]
            }
        )
        factory = CipherSuiteFactoryImpl(
            schemeMetadataProviders,
            verifierProviders,
            digestServiceProviders,
            mock()
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
        (1..100).createTestCase { i ->
            val config = when(i % 3) {
                1 -> {
                    CryptoLibraryConfigImpl(
                        mapOf(
                            "isDev" to "false",
                            "defaultCryptoService" to emptyMap<String, Any?>(),
                            "publicKeys" to emptyMap<String, Any?>(),
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
                    CryptoLibraryConfigImpl(
                        mapOf(
                            "isDev" to "false",
                            "defaultCryptoService" to emptyMap<String, Any?>(),
                            "publicKeys" to emptyMap<String, Any?>(),
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
                    CryptoLibraryConfigImpl(
                        mapOf(
                            "isDev" to "false",
                            "defaultCryptoService" to emptyMap<String, Any?>(),
                            "publicKeys" to emptyMap<String, Any?>(),
                            "rpc" to emptyMap<String, Any?>()
                        )
                    )
                }
            }
            factory.handleConfigEvent(config)
            assertTrue(factory.isRunning)
            assertNotNull(factory.getSchemeMap())
            assertNotNull(factory.getSignatureVerificationService())
            assertNotNull(factory.getDigestService())
        }.runAndValidate()
        factory.stop()
        assertFalse(factory.isRunning)
    }
}