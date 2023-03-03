package net.corda.v5.crypto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SignatureSpecTests {
    @Test
    fun `Should throw IllegalArgumentException when initializing with blank signature name`() {
        assertThrows<IllegalArgumentException> {
            SignatureSpec(" ")
        }
    }
}