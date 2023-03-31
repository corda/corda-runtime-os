package net.corda.membership.locally.hosted.identities.impl

import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PublicKeyFactoryTest {
    private companion object {
        const val VALID_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7fFAKpIU1LlAf7S2n4847JcqvgaT
rVwmQ9GmruPVpC2wEPpFggbVL3/mtm61qoCi81hphd0W9yVhSVwVT0wQHw==
-----END PUBLIC KEY-----
        """

        const val PRIVATE_KEY_PEM = """
-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIHRdEfnecOusz8jAMmaLW2VAlUm97ldXJNa5HOt5l96NoAoGCCqGSM49
AwEHoUQDQgAE6T7NTQnXOUqt/eEEeUhwEHl4PARqAXdrHh4Ae+OWV0VQtDfJD0pl
GtVshXsBhOBt4/fqJyNoxXDHi6rthUX3ww==
-----END EC PRIVATE KEY-----
        """
    }

    @Test
    fun `factory create a public key from a PEM source`() {
        val key = publicKeyFactory(VALID_KEY_PEM)

        assertThat(key.algorithm).isEqualTo("EC")
    }

    @Test
    fun `factory throws an exception for non public key source`() {
        assertThrows<CordaRuntimeException> {
            publicKeyFactory(PRIVATE_KEY_PEM)
        }
    }
}
