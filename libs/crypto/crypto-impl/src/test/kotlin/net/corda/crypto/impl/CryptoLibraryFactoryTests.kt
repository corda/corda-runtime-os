package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertSame

class CryptoLibraryFactoryTests {
    @Test
    fun `Should delegate service creation to cipher suite factory`() {
        val digest = mock< DigestService>()
        val verifier = mock<SignatureVerificationService>()
        val metadata = mock<CipherSchemeMetadata>()
        val cipherSuiteFactory = mock<CipherSuiteFactory>()
        whenever(
            cipherSuiteFactory.getDigestService()
        ).thenReturn(digest)
        whenever(
            cipherSuiteFactory.getSignatureVerificationService()
        ).thenReturn(verifier)
        whenever(
            cipherSuiteFactory.getSchemeMap()
        ).thenReturn(metadata)
        val factory = CryptoLibraryFactoryImpl(cipherSuiteFactory)
        assertSame(digest, factory.getDigestService())
        assertSame(verifier, factory.getSignatureVerificationService())
        assertSame(metadata, factory.getCipherSchemeMetadata())
    }
}