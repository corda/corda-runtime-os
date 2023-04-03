package net.corda.crypto.cipher.suite
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SignatureSpecTests {
    @Test
    fun `Should throw IllegalArgumentException when initializing with blank signature name`() {
        assertThrows<IllegalArgumentException> {
            SignatureSpecImpl(" ")
        }
    }
}
