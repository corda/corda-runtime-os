package net.corda.cipher.suite.providers

import net.corda.crypto.impl.components.CipherSuiteFactoryImpl
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertNotNull

class SignatureVerificationServiceProviderTests {
    @Test
    fun `Should create instance of the service`() {
        val schemeMetadataFactory = CipherSuiteFactoryImpl(
            schemeMetadataProvider = CipherSchemeMetadataProviderImpl(),
            verifierProvider = SignatureVerificationServiceProviderImpl(),
            digestServiceProvider = DigestServiceProviderImpl(),
            mock()
        )
        assertNotNull(SignatureVerificationServiceProviderImpl().getInstance(schemeMetadataFactory))
    }
}