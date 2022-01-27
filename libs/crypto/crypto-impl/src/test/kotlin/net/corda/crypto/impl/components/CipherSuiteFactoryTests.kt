package net.corda.crypto.impl.components

import net.corda.test.util.createTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class CipherSuiteFactoryTests {
    private lateinit var factory: CipherSuiteFactoryImpl

    @BeforeEach
    fun setup() {
        factory = CipherSuiteFactoryImpl(
            null,
            mock()
        )
    }

    @Test
    @Timeout(30)
    fun `Should create always same instances of services`() {
        val schemeMetadata = factory.getSchemeMap()
        assertSame(schemeMetadata, factory.getSchemeMap())
        assertSame(schemeMetadata, factory.getSchemeMap())
        val verifier = factory.getSignatureVerificationService()
        assertSame(verifier, factory.getSignatureVerificationService())
        assertSame(verifier, factory.getSignatureVerificationService())
        val digestService = factory.getDigestService()
        assertSame(digestService, factory.getDigestService())
        assertSame(digestService, factory.getDigestService())
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