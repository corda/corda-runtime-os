package net.corda.crypto.cipher.suite

import net.corda.v5.crypto.DigestAlgorithmName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CustomSignatureSpecTests {
    @Test
    fun `Should throw IllegalArgumentException when initializing with blank signature name`() {
        assertThrows<IllegalArgumentException> {
            CustomSignatureSpec(
                signatureName = "  ",
                customDigestName = DigestAlgorithmName.SHA2_256
            )
        }
    }
}