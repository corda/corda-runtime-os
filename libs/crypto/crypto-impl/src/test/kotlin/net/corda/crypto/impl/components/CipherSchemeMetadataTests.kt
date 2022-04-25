package net.corda.crypto.impl.components

import net.corda.crypto.impl.generateKeyPair
import net.corda.crypto.impl.schememetadata.CordaSecureRandomService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SM2_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SPHINCS256_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CipherSchemeMetadataTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var unknownSignatureSpec: SignatureSpec
        private lateinit var unknownScheme: SignatureScheme

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            unknownSignatureSpec = SignatureSpec(
                signatureName = "na",
                signatureOID = AlgorithmIdentifier(PKCSObjectIdentifiers.RC2_CBC, null)
            )
            unknownScheme = SignatureScheme(
                codeName = "UNKNOWN_SIGNATURE_SCHEME",
                algorithmOIDs = listOf(
                    AlgorithmIdentifier(PKCSObjectIdentifiers.RC2_CBC, null)
                ),
                providerName = "SUN",
                algorithmName = CompositeKey.KEY_ALGORITHM,
                algSpec = null,
                keySize = null,
                signatureSpec = unknownSignatureSpec,
            )
        }

        @JvmStatic
        fun schemes(): Array<SignatureScheme> = schemeMetadata.schemes

        @JvmStatic
        fun supportedSignatureParamSpecs(): Array<AlgorithmParameterSpec> = arrayOf(
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
    }

    @Test
    fun `Should use SecureRandom provided by PlatformSecureRandomService`() {
        assertEquals(CordaSecureRandomService.algorithm, schemeMetadata.secureRandom.algorithm)
    }

    @Test
    fun `SecureRandom should generate new random data each time it's used`() {
        val data1 = ByteArray(37)
        val data2 = ByteArray(37)
        schemeMetadata.secureRandom.nextBytes(data1)
        schemeMetadata.secureRandom.nextBytes(data2)
        var equal = 0
        for (i in 0..36) {
            if (data1[i] == data2[i]) {
                equal++
            }
        }
        assertNotEquals(37, equal)
    }

    @Test
    fun `Should not contain banned digest algorithms`() {
        schemeMetadata.digests.forEach {
            assertFalse(
                CipherSchemeMetadata.BANNED_DIGESTS.any { d -> d == it.algorithmName },
                "Should not contain $it digest."
            )
        }
    }

    @Test
    fun `Should contain at least minimal set of digest algorithms`() {
        assertTrue(schemeMetadata.digests.size > 1)
        assertTrue(schemeMetadata.digests.any { it.algorithmName == "SHA-256" })
        assertTrue(schemeMetadata.digests.any { it.algorithmName == "SHA-384" })
        assertTrue(schemeMetadata.digests.any { it.algorithmName == "SHA-512" })
        assertTrue(schemeMetadata.digests.any { it.algorithmName == "SHA3-256" })
        assertTrue(schemeMetadata.digests.any { it.algorithmName == "SHA3-384" })
        assertTrue(schemeMetadata.digests.any { it.algorithmName == "SHA3-512" })
    }

    @Test
    fun `Should contain predefined list of signature schemes`() {
        assertEquals(8, schemeMetadata.schemes.size)
        assertTrue(schemeMetadata.schemes.any { it.codeName == RSA_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == ECDSA_SECP256K1_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == ECDSA_SECP256R1_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == EDDSA_ED25519_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == SM2_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == GOST3410_GOST3411_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == SPHINCS256_CODE_NAME })
        assertTrue(schemeMetadata.schemes.any { it.codeName == COMPOSITE_KEY_CODE_NAME })
    }

    @Test
    fun `Should contain predefined list of providers`() {
        assertEquals(3, schemeMetadata.providers.size)
        assertTrue(schemeMetadata.providers.containsKey("Corda"))
        assertTrue(schemeMetadata.providers.containsKey("BC"))
        assertTrue(schemeMetadata.providers.containsKey("BCPQC"))
    }

    @Test
    fun `findScheme should throw IllegalArgumentException if the algorithm is not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findSignatureScheme(AlgorithmIdentifier(PKCSObjectIdentifiers.RC2_CBC, null))
        }
    }

    @Test
    fun `findScheme should throw IllegalArgumentException if the scheme code name is not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findSignatureScheme(unknownScheme.codeName)
        }
    }

    @Test
    fun `findScheme should throw IllegalArgumentException if the public key is not supported`() {
        val publicKey = UnsupportedPublicKey()
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findSignatureScheme(publicKey)
        }
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find schemes for all supported scheme code names`(
        signatureScheme: SignatureScheme
    ) {
        val result = schemeMetadata.findSignatureScheme(signatureScheme.codeName)
        assertEquals(signatureScheme, result)
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find schemes for all supported signing algorithms`(
        signatureScheme: SignatureScheme
    ) {
        assumeTrue(signatureScheme.algorithmOIDs.isNotEmpty())
        val result = schemeMetadata.findSignatureScheme(signatureScheme.algorithmOIDs[0])
        assertEquals(signatureScheme, result)
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find schemes for all supported public keys`(
        signatureScheme: SignatureScheme
    ) {
        val publicKey = if (signatureScheme.codeName == COMPOSITE_KEY_CODE_NAME) {
            val alicePublicKey = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME).public
            val bobPublicKey = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME).public
            val charliePublicKey = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME).public
            val aliceAndBob = CompositeKey.Builder()
                .addKey(alicePublicKey, 2)
                .addKey(bobPublicKey, 1)
                .build(threshold = 2)
            CompositeKey.Builder()
                .addKey(aliceAndBob, 3)
                .addKey(charliePublicKey, 2)
                .build(threshold = 3)
        } else {
            generateKeyPair(schemeMetadata, signatureScheme.codeName).public
        }
        val result = schemeMetadata.findSignatureScheme(publicKey)
        assertEquals(signatureScheme, result)
    }

    @Test
    fun `findKeyFactory should throw IllegalArgumentException if the scheme not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findKeyFactory(unknownScheme)
        }
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find key factories for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val factory = schemeMetadata.findKeyFactory(signatureScheme)
        assertEquals(signatureScheme.providerName, factory.provider.name)
    }

    @ParameterizedTest
    @MethodSource("supportedSignatureParamSpecs")
    fun `Should round trip serialize and deserialize supported signature param specs`(params: AlgorithmParameterSpec) {
        val data = schemeMetadata.serialize(params)
        val result = schemeMetadata.deserialize(data)
        assertInstanceOf(params::class.java, result)
    }

    @Test
    fun `Should throw IllegalArgumentException when serializing unsupported signature param spec`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.serialize(mock())
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deserializing unsupported signature param spec`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.deserialize(
                SerializedAlgorithmParameterSpec(
                    clazz = UUID.randomUUID().toString(),
                    bytes = ByteArray(100)
                )
            )
        }
    }

    class UnsupportedPublicKey : PublicKey {
        override fun getAlgorithm(): String = "MOCK"

        override fun getFormat(): String = ASN1Encoding.DER

        override fun getEncoded(): ByteArray {
            val keyVector = ASN1EncodableVector()
            val childrenVector = ASN1EncodableVector()
            keyVector.add(DERSequence(childrenVector))
            return SubjectPublicKeyInfo(
                AlgorithmIdentifier(
                    PKCSObjectIdentifiers.RC2_CBC, null
                ),
                DERSequence(keyVector)
            ).encoded
        }
    }
}