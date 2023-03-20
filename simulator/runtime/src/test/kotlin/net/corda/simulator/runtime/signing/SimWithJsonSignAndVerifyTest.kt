package net.corda.simulator.runtime.signing

import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SimWithJsonSignAndVerifyTest {

    private val keyStore = BaseSimKeyStore()
    private val key = keyStore.generateKey("my-alias", HsmCategory.LEDGER, "any scheme will do")

    @Test
    fun `should be able to sign and verify some bytes`() {
        val signed = SimWithJsonSigningService(keyStore)
            .sign("Hello!".toByteArray(), key, SignatureSpec.ECDSA_SHA256)

        assertDoesNotThrow {
            SimWithJsonSignatureVerificationService().verify(
                "Hello!".toByteArray(),
                signed.bytes,
                key,
                SignatureSpec.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `should fail to verify if the key is not the same key`() {
        val newKey = keyStore.generateKey("another-alias", HsmCategory.LEDGER, "any scheme will do")
        val signed = SimWithJsonSigningService(keyStore)
            .sign("Hello!".toByteArray(), key, SignatureSpec.ECDSA_SHA256)

        assertThrows<CryptoSignatureException> {
            SimWithJsonSignatureVerificationService().verify(
                "Hello!".toByteArray(),
                signed.bytes,
                newKey,
                SignatureSpec.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `should fail to verify if the signature spec does not match`() {
        val signed = SimWithJsonSigningService(keyStore)
            .sign("Hello!".toByteArray(), key, SignatureSpec.ECDSA_SHA256)

        assertThrows<CryptoSignatureException> {
            SimWithJsonSignatureVerificationService().verify(
                "Hello!".toByteArray(),
                signed.bytes,
                key,
                SignatureSpec.ECDSA_SHA384
            )
        }
    }

    @Test
    fun `should fail to verify if original data does not match`() {
        val signed = SimWithJsonSigningService(keyStore)
            .sign("Hello!".toByteArray(), key, SignatureSpec.ECDSA_SHA256)

        assertThrows<CryptoSignatureException> {
            SimWithJsonSignatureVerificationService().verify(
                "Goodbye!".toByteArray(),
                signed.bytes,
                key,
                SignatureSpec.ECDSA_SHA256
            )
        }
    }
}