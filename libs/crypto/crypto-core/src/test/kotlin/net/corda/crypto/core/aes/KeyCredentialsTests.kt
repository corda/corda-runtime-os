package net.corda.crypto.core.aes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeyCredentialsTests {
    @Test
    fun `Should throw IllegalArgumentException when passphrase is blank`() {
        assertThrows<IllegalArgumentException> {
            KeyCredentials("  ", "salt")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when salt is blank`() {
        assertThrows<IllegalArgumentException> {
            KeyCredentials("passphrase", "  ")
        }
    }
}