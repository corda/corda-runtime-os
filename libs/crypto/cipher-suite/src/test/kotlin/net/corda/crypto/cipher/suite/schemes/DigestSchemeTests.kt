package net.corda.crypto.cipher.suite.schemes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DigestSchemeTests {
    @Test
    fun `Should throw IllegalArgumentException when initializing with blank algorithm name`() {
        assertThrows<IllegalArgumentException> {
            DigestScheme(
                algorithmName = "  ",
                providerName = "BC"
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when initializing with blank provider name`() {
        assertThrows<IllegalArgumentException> {
            DigestScheme(
                algorithmName = "SHA-384",
                providerName = "  "
            )
        }
    }
}