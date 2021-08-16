package net.corda.impl.cipher.suite

import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.CompositeKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class CordaSecurityProviderTests {
    companion object {
        lateinit var cryptoMocks: CryptoMocks
        lateinit var schemeMetadata: CipherSchemeMetadata

        @BeforeAll
        @JvmStatic
        fun setup() {
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata()
        }
    }

    @Test
    @Timeout(30)
    fun `get security provider algorithm services`() {
        val keyFactoryType = "KeyFactory"
        val signatureType = "Signature"
        val cordaSecurityProvider = CordaSecurityProvider { schemeMetadata }

        val keyFactoryService = cordaSecurityProvider.getService(keyFactoryType, CompositeKey.KEY_ALGORITHM)
        val signatureService = cordaSecurityProvider.getService(signatureType, CompositeSignature.SIGNATURE_ALGORITHM)

        assertEquals(keyFactoryType, keyFactoryService!!.type)
        assertEquals(CompositeKey.KEY_ALGORITHM, keyFactoryService.algorithm)
        assertEquals(CompositeKeyFactory::class.java.name, keyFactoryService.className)

        assertEquals(signatureType, signatureService!!.type)
        assertEquals(CompositeSignature.SIGNATURE_ALGORITHM, signatureService.algorithm)
        assertEquals(CompositeSignature::class.java.name, signatureService.className)
    }
}