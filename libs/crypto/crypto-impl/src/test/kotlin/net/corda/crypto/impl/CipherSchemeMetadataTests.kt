package net.corda.crypto.impl

import net.corda.crypto.CryptoConsts
import net.corda.crypto.SigningService
import net.corda.crypto.impl.stubs.CryptoServicesTestFactory
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CipherSchemeMetadataTests {
    companion object {
        private lateinit var schemeMetadataFactory: CipherSchemeMetadataFactory
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var unknownSignatureSpec: SignatureSpec
        private lateinit var unknownScheme: SignatureScheme
        private lateinit var factory: CryptoServicesTestFactory
        private lateinit var services: CryptoServicesTestFactory.CryptoServices

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadataFactory = CipherSchemeMetadataFactory()
            schemeMetadata = schemeMetadataFactory.getInstance()
            factory = CryptoServicesTestFactory(schemeMetadata)
            services = factory.createCryptoServices()
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

        private fun getSigner(defaultSignatureSchemeCodeName: String): SigningService =
            services.createSigningService(
                schemeMetadata.findSignatureScheme(defaultSignatureSchemeCodeName)
            )
    }

    @Test
    @Timeout(30)
    fun `Should use SecureRandom provided by PlatformSecureRandomService`() {
        assertEquals(CordaSecureRandomService.algorithm, schemeMetadata.secureRandom.algorithm)
    }

    @Test
    @Timeout(30)
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
    @Timeout(30)
    fun `Should not contain banned digest algorithms`() {
        schemeMetadata.digests.forEach {
            assertFalse(
                CipherSchemeMetadata.BANNED_DIGESTS.any { d -> d == it.algorithmName },
                "Should not contain $it digest."
            )
        }
    }

    @Test
    @Timeout(30)
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
    @Timeout(30)
    fun `Should contain predefined list of signature schemes`() {
        assertEquals(8, schemeMetadata.schemes.size)
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.RSA_SHA256))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.ECDSA_SECP256K1_SHA256))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.ECDSA_SECP256R1_SHA256))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.EDDSA_ED25519_NONE))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.SM2_SM3))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.GOST3410_GOST3411))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.SPHINCS256_SHA512))
        assertTrue(schemeMetadata.schemes.contains(schemeMetadataFactory.providerMap.COMPOSITE_KEY))
    }

    @Test
    @Timeout(30)
    fun `Should contain predefined list of providers`() {
        assertEquals(3, schemeMetadata.providers.size)
        assertTrue(schemeMetadata.providers.containsKey("Corda"))
        assertTrue(schemeMetadata.providers.containsKey("BC"))
        assertTrue(schemeMetadata.providers.containsKey("BCPQC"))
    }

    @Test
    @Timeout(30)
    fun `findScheme should throw IllegalArgumentException if the algorithm is not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findSignatureScheme(AlgorithmIdentifier(PKCSObjectIdentifiers.RC2_CBC, null))
        }
    }

    @Test
    @Timeout(30)
    fun `findScheme should throw IllegalArgumentException if the scheme code name is not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findSignatureScheme(unknownScheme.codeName)
        }
    }

    @Test
    @Timeout(30)
    fun `findScheme should throw IllegalArgumentException if the public key is not supported`() {
        val publicKey = UnsupportedPublicKey()
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findSignatureScheme(publicKey)
        }
    }

    @ParameterizedTest
    @MethodSource("schemes")
    @Timeout(30)
    fun `Should find schemes for all supported scheme code names`(
        signatureScheme: SignatureScheme
    ) {
        val result = schemeMetadata.findSignatureScheme(signatureScheme.codeName)
        assertEquals(signatureScheme, result)
    }

    @ParameterizedTest
    @MethodSource("schemes")
    @Timeout(30)
    fun `Should find schemes for all supported signing algorithms`(
        signatureScheme: SignatureScheme
    ) {
        assumeTrue(signatureScheme.algorithmOIDs.isNotEmpty())
        val result = schemeMetadata.findSignatureScheme(signatureScheme.algorithmOIDs[0])
        assertEquals(signatureScheme, result)
    }

    @ParameterizedTest
    @MethodSource("schemes")
    @Timeout(30)
    fun `Should find schemes for all supported public keys`(
        signatureScheme: SignatureScheme
    ) {
        val publicKey = if (signatureScheme.codeName == COMPOSITE_KEY_CODE_NAME) {
            val signer = getSigner(EDDSA_ED25519_CODE_NAME)
            val alicePublicKey = signer.generateKeyPair(CryptoConsts.CryptoCategories.LEDGER, newAlias())
            val bobPublicKey = signer.generateKeyPair(CryptoConsts.CryptoCategories.LEDGER, newAlias())
            val charliePublicKey = signer.generateKeyPair(CryptoConsts.CryptoCategories.LEDGER, newAlias())
            val aliceAndBob = CompositeKey.Builder()
                .addKey(alicePublicKey, 2)
                .addKey(bobPublicKey, 1)
                .build(threshold = 2)
            CompositeKey.Builder()
                .addKey(aliceAndBob, 3)
                .addKey(charliePublicKey, 2)
                .build(threshold = 3)
        } else {
            val signer = getSigner(signatureScheme.codeName)
            signer.generateKeyPair(CryptoConsts.CryptoCategories.LEDGER, newAlias())
        }
        val result = schemeMetadata.findSignatureScheme(publicKey)
        assertEquals(signatureScheme, result)
    }

    @Test
    @Timeout(30)
    fun `findKeyFactory should throw IllegalArgumentException if the scheme not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findKeyFactory(unknownScheme)
        }
    }

    @ParameterizedTest
    @MethodSource("schemes")
    @Timeout(30)
    fun `Should find key factories for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val factory = schemeMetadata.findKeyFactory(signatureScheme)
        assertEquals(signatureScheme.providerName, factory.provider.name)
    }

    private fun newAlias() = UUID.randomUUID().toString()

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