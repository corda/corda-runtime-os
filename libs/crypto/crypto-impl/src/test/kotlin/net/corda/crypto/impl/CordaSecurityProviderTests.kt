package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.impl.CompositeKeyImpl.Companion.KEY_ALGORITHM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CordaSecurityProviderTests {
    companion object {
        lateinit var keyEncodingService: KeyEncodingService
        @BeforeAll
        @JvmStatic
        fun setup() {
            keyEncodingService = mock()
        }
    }

    @Test
    fun `get security provider algorithm services`() {
        val keyFactoryType = "KeyFactory"
        val signatureType = "Signature"
        val cordaSecurityProvider = CordaSecurityProvider(keyEncodingService)

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