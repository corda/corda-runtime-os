package net.corda.v5.crypto

import net.corda.v5.crypto.mocks.DigestServiceMock
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest
import java.util.UUID

class SignatureSpecTests {
    companion object {
        private lateinit var digestService: DigestService

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = DigestServiceMock()
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when initializing with blank signature name`() {
        assertThrows<IllegalArgumentException> {
            SignatureSpec(
                signatureName = "  "
            )
        }
    }

    @Test
    fun `getSigningData should returned passed byte array for standard digest calculation`() {
        val spec = ECDSA_SHA256_SIGNATURE_SPEC
        val data = UUID.randomUUID().toString().toByteArray()
        assertFalse(spec.precalculateHash)
        assertArrayEquals(data, spec.getSigningData(digestService, data))
    }

    @Test
    fun `getSigningData should returned digest byte array for precalculated digest`() {
        val spec = SignatureSpec(
            signatureName = "NONEwithECDSA",
            customDigestName = DigestAlgorithmName.SHA2_256
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertTrue(spec.precalculateHash)
        assertArrayEquals(expected, spec.getSigningData(digestService, data))
    }
}
