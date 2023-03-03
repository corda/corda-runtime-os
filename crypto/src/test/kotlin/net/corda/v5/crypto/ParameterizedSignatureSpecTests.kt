package net.corda.v5.crypto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec

class ParameterizedSignatureSpecTests {
    @Test
    fun `Should throw IllegalArgumentException when initializing with blank signature name`() {
        assertThrows<IllegalArgumentException> {
            ParameterizedSignatureSpec(
                "  ",
                PSSParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    32,
                    1
                )
            )
        }
    }
 }