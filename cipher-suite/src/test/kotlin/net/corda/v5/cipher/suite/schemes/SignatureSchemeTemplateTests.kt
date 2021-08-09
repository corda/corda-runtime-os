package net.corda.v5.cipher.suite.schemes

import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows

class SignatureSchemeTemplateTests {
    @Test
    @Timeout(5)
    fun `Should throw IllegalArgumentException when initializing with blank code name`() {
        assertThrows<IllegalArgumentException> {
            SignatureSchemeTemplate(
                codeName = "  ",
                algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
                algorithmName = "EC",
                algSpec = null,
                keySize = null,
                signatureSpec = ECDSA_SECP256K1_SHA256_TEMPLATE.signatureSpec
            )
        }
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalArgumentException when initializing with empty algorithmOIDs`() {
        assertThrows<IllegalArgumentException> {
            SignatureSchemeTemplate(
                codeName = ECDSA_SECP256K1_CODE_NAME,
                algorithmOIDs = emptyList(),
                algorithmName = "EC",
                algSpec = null,
                keySize = null,
                signatureSpec = ECDSA_SECP256K1_SHA256_TEMPLATE.signatureSpec
            )
        }
    }

    @Test
    @Timeout(5)
    fun `Should throw IllegalArgumentException when initializing with blank algorithm name`() {
        assertThrows<IllegalArgumentException> {
            SignatureSchemeTemplate(
                codeName = ECDSA_SECP256K1_CODE_NAME,
                algorithmOIDs = listOf(AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1)),
                algorithmName = "  ",
                algSpec = null,
                keySize = null,
                signatureSpec = ECDSA_SECP256K1_SHA256_TEMPLATE.signatureSpec
            )
        }
    }
}