package net.corda.crypto.cipher.suite.schemes

import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeySchemeTests {
    @Test
    fun `Should throw IllegalArgumentException when initializing with blank code name`() {
        assertThrows<IllegalArgumentException> {
            KeyScheme(
                codeName = "  ",
                algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
                providerName = "provider",
                algorithmName = "EC",
                algSpec = null,
                keySize = null,
                capabilities = setOf(KeySchemeCapability.SIGN)
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when initializing with empty algorithmOIDs`() {
        assertThrows<IllegalArgumentException> {
            KeyScheme(
                codeName = ECDSA_SECP256K1_CODE_NAME,
                algorithmOIDs = emptyList(),
                providerName = "provider",
                algorithmName = "EC",
                algSpec = null,
                keySize = null,
                capabilities = setOf(KeySchemeCapability.SIGN)
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when initializing with blank provider name`() {
        assertThrows<IllegalArgumentException> {
            KeyScheme(
                codeName = ECDSA_SECP256K1_CODE_NAME,
                algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
                providerName = "  ",
                algorithmName = "EC",
                algSpec = null,
                keySize = null,
                capabilities = setOf(KeySchemeCapability.SIGN)
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when initializing with blank algorithm name`() {
        assertThrows<IllegalArgumentException> {
            KeyScheme(
                codeName = ECDSA_SECP256K1_CODE_NAME,
                algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
                providerName = "provider",
                algorithmName = "  ",
                algSpec = null,
                keySize = null,
                capabilities = setOf(KeySchemeCapability.SIGN)
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when initializing with empty capabilities`() {
        assertThrows<IllegalArgumentException> {
            KeyScheme(
                codeName = ECDSA_SECP256K1_CODE_NAME,
                algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
                providerName = "provider",
                algorithmName = "some-algorithm",
                algSpec = null,
                keySize = null,
                capabilities = emptySet()
            )
        }
    }
}