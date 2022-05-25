package net.corda.crypto.impl.components

import net.corda.crypto.core.DefaultSignatureOIDMap
import net.corda.crypto.impl.infra.generateKeyPair
import net.corda.crypto.impl.infra.inferSignatureSpecOrCreateDefault
import net.corda.crypto.impl.infra.signData
import net.corda.crypto.impl.schememetadata.CordaSecureRandomService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import net.corda.v5.crypto.COMPOSITE_KEY_CODE_NAME
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureVerificationService
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class CipherSchemeMetadataTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var unknownScheme: KeyScheme
        private lateinit var verifier: SignatureVerificationService
        private lateinit var testParams: List<Arguments>

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            unknownScheme = KeyScheme(
                codeName = "UNKNOWN_SIGNATURE_SCHEME",
                algorithmOIDs = listOf(
                    AlgorithmIdentifier(PKCSObjectIdentifiers.RC2_CBC, null)
                ),
                providerName = "SUN",
                algorithmName = CompositeKey.KEY_ALGORITHM,
                algSpec = null,
                keySize = null
            )
            val digest = DigestServiceImpl(schemeMetadata, null)
            verifier = SignatureVerificationServiceImpl(schemeMetadata, digest)
            testParams = schemeMetadata.schemes.filter {
                it.codeName != COMPOSITE_KEY_CODE_NAME
            }.map {
                Arguments.of(
                    generateKeyPair(schemeMetadata, it.codeName),
                    generateKeyPair(schemeMetadata, it.codeName),
                    generateKeyPair(schemeMetadata, it.codeName)
                )
            }
        }

        @JvmStatic
        fun keyPairs(): List<Arguments> = testParams

        @JvmStatic
        fun schemes(): List<KeyScheme> = schemeMetadata.schemes

        @JvmStatic
        fun supportedSignatureParamSpecs(): List<AlgorithmParameterSpec> = listOf(
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
    fun `findKeyScheme should throw IllegalArgumentException if the algorithm is not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findKeyScheme(AlgorithmIdentifier(PKCSObjectIdentifiers.RC2_CBC, null))
        }
    }

    @Test
    fun `findKeyScheme should throw IllegalArgumentException if the scheme code name is not supported`() {
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findKeyScheme(unknownScheme.codeName)
        }
    }

    @Test
    fun `findKeyScheme should throw IllegalArgumentException if the public key is not supported`() {
        val publicKey = UnsupportedPublicKey()
        assertThrows<IllegalArgumentException> {
            schemeMetadata.findKeyScheme(publicKey)
        }
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find schemes for all supported scheme code names`(
        signatureScheme: KeyScheme
    ) {
        val result = schemeMetadata.findKeyScheme(signatureScheme.codeName)
        assertEquals(signatureScheme, result)
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find schemes for all supported signing algorithms`(
        signatureScheme: KeyScheme
    ) {
        assumeTrue(signatureScheme.algorithmOIDs.isNotEmpty())
        val result = schemeMetadata.findKeyScheme(signatureScheme.algorithmOIDs[0])
        assertEquals(signatureScheme, result)
    }

    @ParameterizedTest
    @MethodSource("schemes")
    fun `Should find schemes for all supported public keys`(
        signatureScheme: KeyScheme
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
        val result = schemeMetadata.findKeyScheme(publicKey)
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
        signatureScheme: KeyScheme
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

    @Test
    fun `Should be able to infer RSA signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == RSA_CODE_NAME
        }.get()[0] as KeyPair
        listOf("SHA-256", "SHA-384", "SHA-512").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec)
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to infer ECDSA SECP256R1 signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == ECDSA_SECP256R1_CODE_NAME
        }.get()[0] as KeyPair
        listOf("SHA-256", "SHA-384", "SHA-512").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec)
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should be able to infer ECDSA SECP256K1 signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == ECDSA_SECP256K1_CODE_NAME
        }.get()[0] as KeyPair
        listOf("SHA-256", "SHA-384", "SHA-512").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec)
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @Test
    fun `Should be able to infer EDDSA signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == EDDSA_ED25519_CODE_NAME
        }.get()[0] as KeyPair
        listOf("NONE").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec)
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @Test
    fun `Should be able to infer SM2 signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == SM2_CODE_NAME
        }.get()[0] as KeyPair
        listOf("SM3", "SHA-256").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec)
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @Test
    fun `Should be able to infer SPHINCS256_ signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == SPHINCS256_CODE_NAME
        }.get()[0] as KeyPair
        listOf("SHA-512").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec)
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @Test
    fun `Should be able to infer GOST3410 signature spec for all supported digests and use it to sign and verify`() {
        val keyPair = testParams.first {
            schemeMetadata.findKeyScheme((it.get()[0] as KeyPair).public).codeName == GOST3410_GOST3411_CODE_NAME
        }.get()[0] as KeyPair
        listOf("GOST3411").forEach {
            val data = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.inferSignatureSpec(keyPair.public, DigestAlgorithmName(it))
            assertNotNull(signatureSpec, "digest=$it")
            val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
            verifier.verify(keyPair.public, signatureSpec, signature, data)
        }
    }

    @ParameterizedTest
    @MethodSource("keyPairs")
    fun `Should convert public key to PEM and back and still to able to use for verification for all supported schemes`(
        keyPair: KeyPair
    ) {
        val encodedPublicKey = schemeMetadata.encodeAsString(keyPair.public)
        assert(encodedPublicKey.startsWith("-----BEGIN PUBLIC KEY-----")) { encodedPublicKey }
        assert(encodedPublicKey.contains("-----END PUBLIC KEY-----")) { encodedPublicKey }
        val decodedPublicKey = schemeMetadata.decodePublicKey(encodedPublicKey)
        assertEquals(decodedPublicKey, keyPair.public)
        val data = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val signatureSpec = schemeMetadata.inferSignatureSpecOrCreateDefault(
            decodedPublicKey,
            DigestAlgorithmName.SHA2_256
        )
        val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
        kotlin.test.assertTrue(
            verifier.isValid(decodedPublicKey, signatureSpec, signature, data),
            "algorithm=${keyPair.public.algorithm}, scheme=${
                schemeMetadata.findKeyScheme(
                    keyPair.public
                ).codeName
            }"
        )
    }

    @ParameterizedTest
    @MethodSource("keyPairs")
    @Suppress("MaxLineLength")
    fun `Should convert public key to byte array and back and and still to able to use for verification for all supported schemes`(
        keyPair: KeyPair
    ) {
        val encodedPublicKey = schemeMetadata.encodeAsByteArray(keyPair.public)
        val decodedPublicKey = schemeMetadata.decodePublicKey(encodedPublicKey)
        assertEquals(decodedPublicKey, keyPair.public)
        val data = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val signatureSpec = schemeMetadata.inferSignatureSpecOrCreateDefault(decodedPublicKey, DigestAlgorithmName.SHA2_256)
        val signature = signData(schemeMetadata, signatureSpec, keyPair, data)
        kotlin.test.assertTrue(
            verifier.isValid(
                decodedPublicKey,
                signatureSpec,
                signature,
                data
            )
        )
    }

    @ParameterizedTest
    @MethodSource("keyPairs")
    fun `Should round trip encode CompositeKey to byte array with keys for all supported schemes`(
        keyPair1: KeyPair,
        keyPair2: KeyPair,
        keyPair3: KeyPair
    ) {
        val alicePublicKey = keyPair1.public
        val bobPublicKey = keyPair2.public
        val charliePublicKey = keyPair3.public
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKeys(aliceAndBob, charliePublicKey)
            .build(threshold = 1)
        val encoded = schemeMetadata.encodeAsByteArray(aliceAndBobOrCharlie)
        val decoded = schemeMetadata.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("keyPairs")
    fun `Should round trip encode CompositeKey to PEM with keys for all supported schemes`(
        keyPair1: KeyPair,
        keyPair2: KeyPair,
        keyPair3: KeyPair
    ) {
        val alicePublicKey = keyPair1.public
        val bobPublicKey = keyPair2.public
        val charliePublicKey = keyPair3.public
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKeys(aliceAndBob, charliePublicKey)
            .build(threshold = 1)
        val encoded = schemeMetadata.encodeAsString(aliceAndBobOrCharlie)
        val decoded = schemeMetadata.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("keyPairs")
    fun `Should round trip encode CompositeKey with weighting to byte array with keys for all supported schemes`(
        keyPair1: KeyPair,
        keyPair2: KeyPair,
        keyPair3: KeyPair
    ) {
        val alicePublicKey = keyPair1.public
        val bobPublicKey = keyPair2.public
        val charliePublicKey = keyPair3.public
        val aliceAndBob = CompositeKey
            .Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = schemeMetadata.encodeAsByteArray(aliceAndBobOrCharlie)
        val decoded = schemeMetadata.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @ParameterizedTest
    @MethodSource("keyPairs")
    fun `Should round trip encode CompositeKey with weighting to PEM with keys for all supported schemes`(
        keyPair1: KeyPair,
        keyPair2: KeyPair,
        keyPair3: KeyPair
    ) {
        val alicePublicKey = keyPair1.public
        val bobPublicKey = keyPair2.public
        val charliePublicKey = keyPair3.public
        val aliceAndBob = CompositeKey
            .Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val encoded = schemeMetadata.encodeAsString(aliceAndBobOrCharlie)
        val decoded = schemeMetadata.decodePublicKey(encoded)
        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Suppress("TooGenericExceptionThrown")
    @ParameterizedTest
    @MethodSource("keyPairs")
    fun `Test save to keystore with keys for all supported schemes`(
        keyPair1: KeyPair,
        keyPair2: KeyPair,
        keyPair3: KeyPair
    ) {
        val alicePublicKey = keyPair1.public
        val bobPublicKey = keyPair2.public
        val charliePublicKey = keyPair3.public
        val aliceAndBob = CompositeKey
            .Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val aliceAndBobOrCharlie = CompositeKey
            .Builder()
            .addKey(aliceAndBob, 3)
            .addKey(charliePublicKey, 2)
            .build(threshold = 3)
        val subjectAlias = newAlias()
        val pwdArray = "password".toCharArray()
        val keyStoreSave = KeyStore.getInstance("JKS")
        keyStoreSave.load(null, pwdArray)
        val scheme = schemeMetadata.findKeyScheme(keyPair1.public)
        val caKeyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val signatureSpec = schemeMetadata.inferSignatureSpecOrCreateDefault(caKeyPair.public, DigestAlgorithmName.SHA2_256)
        val jksFile = ByteArrayOutputStream().use {
            keyStoreSave.setCertificateEntry(
                subjectAlias, createDevCertificate(
                    issuer = X500Name("CN=ISSUER, O=o, L=L, ST=il, C=c"),
                    signer = object : ContentSigner {
                        private val sigAlgID: AlgorithmIdentifier = DefaultSignatureOIDMap.inferSignatureOID(
                            caKeyPair.public,
                            signatureSpec
                        ) ?: throw Exception(
                            "No IOD for ${caKeyPair.public.algorithm} and scheme=${scheme.codeName}"
                        )
                        private val baos = ByteArrayOutputStream()
                        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgID
                        override fun getOutputStream(): OutputStream = baos
                        override fun getSignature(): ByteArray = signData(
                            schemeMetadata,
                            signatureSpec,
                            caKeyPair,
                            baos.toByteArray()
                        )
                    },
                    subject = X500Name("CN=SUBJECT, O=o, L=L, ST=il, C=c"),
                    subjectPublicKey = aliceAndBobOrCharlie
                )
            )
            keyStoreSave.store(it, pwdArray)
            it
        }.toByteArray()
        val keyStoreRead = KeyStore.getInstance("JKS")
        val loadedKey = jksFile.inputStream().use {
            keyStoreRead.load(it, pwdArray)
            schemeMetadata.decodePublicKey(keyStoreRead.getCertificate(subjectAlias).publicKey.encoded)
        }
        assertEquals(aliceAndBobOrCharlie, loadedKey)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun createDevCertificate(
        issuer: X500Name,
        signer: ContentSigner,
        subject: X500Name,
        subjectPublicKey: PublicKey
    ): X509Certificate {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(subjectPublicKey.encoded))
        val validityWindow = getValidityWindow(Duration.ZERO, Duration.ofDays(365))
        val v3CertGen = X509v3CertificateBuilder(
            issuer,
            BigInteger.valueOf(System.currentTimeMillis()),
            Time(validityWindow.first),
            Time(validityWindow.second),
            subject,
            subjectPublicKeyInfo
        )
        return v3CertGen.build(signer).toJca()
    }

    @Suppress("SameParameterValue")
    private fun getValidityWindow(before: Duration, after: Duration): Pair<Date, Date> {
        val startOfDayUTC = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val notBefore = startOfDayUTC - before
        val notAfter = startOfDayUTC + after
        return Pair(Date(notBefore.toEpochMilli()), Date(notAfter.toEpochMilli()))
    }

    private fun X509CertificateHolder.toJca(): X509Certificate =
        requireNotNull(
            CertificateFactory.getInstance("X.509").generateCertificate(
                encoded.inputStream()
            ) as? X509Certificate
        ) {
            "Not an X.509 certificate: $this"
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