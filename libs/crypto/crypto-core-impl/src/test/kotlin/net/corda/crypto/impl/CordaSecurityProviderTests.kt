package net.corda.crypto.impl

import net.corda.crypto.core.service.PlatformCipherSuiteMetadata
import net.corda.crypto.impl.cipher.suite.CompositeKeyFactory
import net.corda.crypto.impl.cipher.suite.CompositeKeyImpl
import net.corda.crypto.impl.cipher.suite.CompositeKeyImpl.Companion.KEY_ALGORITHM
import net.corda.crypto.impl.cipher.suite.CompositeSignature
import net.corda.crypto.impl.cipher.suite.CordaSecurityProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CordaSecurityProviderTests {
    companion object {
        lateinit var encodingHandler: PlatformCipherSuiteMetadata
        @BeforeAll
        @JvmStatic
        fun setup() {
            encodingHandler = mock()
        }
    }

    @Test
    fun `get security provider algorithm services`() {
        val keyFactoryType = "KeyFactory"
        val signatureType = "Signature"
        val cordaSecurityProvider = CordaSecurityProvider(encodingHandler)

        val keyFactoryService = cordaSecurityProvider.getService(keyFactoryType, CompositeKeyImpl.KEY_ALGORITHM)
        val signatureService = cordaSecurityProvider.getService(signatureType, CompositeSignature.SIGNATURE_ALGORITHM)

        assertEquals(keyFactoryType, keyFactoryService!!.type)
        assertEquals(KEY_ALGORITHM, keyFactoryService.algorithm)
        assertEquals(CompositeKeyFactory::class.java.name, keyFactoryService.className)

        assertEquals(signatureType, signatureService!!.type)
        assertEquals(CompositeSignature.SIGNATURE_ALGORITHM, signatureService.algorithm)
        assertEquals(CompositeSignature::class.java.name, signatureService.className)
    }
}