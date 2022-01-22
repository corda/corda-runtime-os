package net.corda.cipher.suite.providers

import net.corda.crypto.impl.components.CipherSuiteFactoryImpl
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class SignatureVerificationServiceProviderTests {
    @Test
    fun `Should create instance of the service`() {
        val schemeMetadataFactory = CipherSuiteFactoryImpl()
        schemeMetadataFactory.schemeMetadataProvider = CipherSchemeMetadataProviderImpl()
        schemeMetadataFactory.digestServiceProvider = DigestServiceProviderImpl()
        assertNotNull(SignatureVerificationServiceProviderImpl().getInstance(schemeMetadataFactory))
    }
}