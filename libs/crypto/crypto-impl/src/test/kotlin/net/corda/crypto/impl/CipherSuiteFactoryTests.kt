package net.corda.crypto.impl

import net.corda.crypto.impl.components.CipherSuiteFactoryImpl
import net.corda.test.util.createTestCase
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CipherSuiteFactoryTests {
    private lateinit var schemeMetadata: CipherSchemeMetadata
    private lateinit var schemeMetadataProvider: CipherSchemeMetadataProvider
    private lateinit var verifier: SignatureVerificationService
    private lateinit var verifierProvider: SignatureVerificationServiceProvider
    private lateinit var digestService: DigestService
    private lateinit var digestServiceProvider: DigestServiceProvider
    private lateinit var factory: CipherSuiteFactoryImpl

    @BeforeEach
    fun setup() {
        schemeMetadata = mock()
        schemeMetadataProvider = mock {
            on { getInstance() }.thenReturn(schemeMetadata)
        }
        verifier = mock()
        verifierProvider = mock {
            on { getInstance(any()) }.thenReturn(verifier)
        }
        digestService =  mock()
        digestServiceProvider = mock {
            on { getInstance(any()) }.thenReturn(digestService)
        }
        factory = CipherSuiteFactoryImpl(
            schemeMetadataProvider,
            verifierProvider,
            digestServiceProvider,
            mock()
        )
    }

    @Test
    @Timeout(30)
    fun `Should create always same instances of services`() {
        assertSame(schemeMetadata, factory.getSchemeMap())
        assertSame(schemeMetadata, factory.getSchemeMap())
        assertSame(verifier, factory.getSignatureVerificationService())
        assertSame(verifier, factory.getSignatureVerificationService())
        assertSame(digestService, factory.getDigestService())
        assertSame(digestService, factory.getDigestService())
        Mockito.verify(schemeMetadataProvider, times(1)).getInstance()
        Mockito.verify(verifierProvider, times(1)).getInstance(factory)
        Mockito.verify(digestServiceProvider, times(1)).getInstance(factory)
    }

    @Test
    @Timeout(30)
    fun `Should concurrently create services`() {
        (1..100).createTestCase {
            assertNotNull(factory.getSchemeMap())
            assertNotNull(factory.getSignatureVerificationService())
            assertNotNull(factory.getDigestService())
        }.runAndValidate()
    }
}