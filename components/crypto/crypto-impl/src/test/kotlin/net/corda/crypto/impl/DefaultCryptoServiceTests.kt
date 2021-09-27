package net.corda.crypto.impl

import net.corda.crypto.impl.persistence.DefaultCryptoKeyCacheImpl
import net.corda.crypto.impl.persistence.DefaultCryptoKeyCache
import net.corda.crypto.testkit.CryptoMocks
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.cipher.suite.schemes.NaSignatureSpec
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SM2_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SPHINCS256_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPair
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.util.Random
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultCryptoServiceTests {
    companion object {
        private const val wrappingKeyAlias = "wrapping-key-alias"
        private val test100ZeroBytes = ByteArray(100)
        private val UNSUPPORTED_SIGNATURE_SCHEME = SignatureScheme(
            codeName = "UNSUPPORTED_SIGNATURE_SCHEME",
            algorithmOIDs = listOf(
                AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)
            ),
            providerName = "SUN",
            algorithmName = CompositeKey.KEY_ALGORITHM,
            algSpec = null,
            keySize = null,
            signatureSpec = NaSignatureSpec
        )
        private val algoAndSchemeAlgoPairs = listOf(
            Pair("NONEwithRSA", "RSA"),
            Pair("SHA224withRSA", "RSA"),
            Pair("SHA256withRSA", "RSA"),
            Pair("SHA384withRSA", "RSA"),
            Pair("SHA512withRSA", "RSA"),
            Pair("NONEwithECDSA", "EC"),
            Pair("SHA1withECDSA", "EC"),
            Pair("SHA224withECDSA", "EC"),
            Pair("SHA256withECDSA", "EC"),
            Pair("SHA384withECDSA", "EC"),
            Pair("SHA512withECDSA", "EC"),
            Pair("NONEwithEdDSA", "EdDSA"),
        )

        private lateinit var memberId: String
        private lateinit var cryptoMocks: CryptoMocks
        private lateinit var signatureVerifier: SignatureVerificationService
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var cryptoServiceCache: DefaultCryptoKeyCache
        private lateinit var cryptoService: DefaultCryptoService

        @JvmStatic
        @BeforeAll
        fun setup() {
            memberId = UUID.randomUUID().toString()
            cryptoMocks = CryptoMocks()
            schemeMetadata = cryptoMocks.schemeMetadata
            signatureVerifier = cryptoMocks.factories.cryptoLibrary.getSignatureVerificationService()
            cryptoServiceCache = DefaultCryptoKeyCacheImpl(
                memberId = memberId,
                passphrase = "PASSPHRASE",
                salt = "SALT",
                schemeMetadata = cryptoMocks.schemeMetadata,
                persistence = cryptoMocks.defaultPersistentKeyCache
            )
            cryptoService = DefaultCryptoService(
                cache = cryptoServiceCache,
                schemeMetadata = schemeMetadata,
                hashingService = cryptoMocks.factories.cryptoLibrary.getDigestService()
            )
            cryptoServiceCache = cryptoService.cache
            cryptoService.createWrappingKey(wrappingKeyAlias, true)
        }

        @JvmStatic
        fun supportedSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedSchemes()
        }

        @JvmStatic
        fun supportedWrappingSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedWrappingSchemes()
        }

        @JvmStatic
        fun signatureSchemesWithPrecalculatedDigest(): List<Arguments> =
            schemeMetadata.digests.flatMap { digest ->
                cryptoService.supportedSchemes().filter { scheme ->
                    scheme.codeName == RSA_CODE_NAME || scheme.codeName == ECDSA_SECP256R1_CODE_NAME
                }.map { scheme ->
                    when (scheme.algorithmName) {
                        "RSA" -> Arguments.of(
                            scheme, SignatureSpec(
                                signatureName = "RSA/NONE/PKCS1Padding",
                                customDigestName = DigestAlgorithmName(digest.algorithmName)
                            )
                        )
                        "EC" -> Arguments.of(
                            scheme, SignatureSpec(
                                signatureName = "NONEwithECDSA",
                                customDigestName = DigestAlgorithmName(digest.algorithmName)
                            )
                        )
                        else -> Arguments.of(
                            scheme, SignatureSpec(
                                signatureName = "NONEwith${scheme.algorithmName}",
                                customDigestName = DigestAlgorithmName(digest.algorithmName)
                            )
                        )
                    }
                }
            }.toList()

        @JvmStatic
        fun schemeAndAlgos(): List<Arguments> {
            return cryptoService.supportedSchemes().flatMap { scheme: SignatureScheme ->
                // Run only for the schemeAlgoName passed in. EC has multiple schemes for different curves,
                // so run for all schemes that this algorithm applies to
                val actualSchemeAlgo = if (scheme.algorithmName == "1.3.101.112") {
                    "EdDSA"
                } else {
                    scheme.algorithmName
                } // Allow ED curve to match
                algoAndSchemeAlgoPairs.mapNotNull { pair ->
                    val (algo, schemeAlgo) = pair
                    if (schemeAlgo == actualSchemeAlgo) Arguments.of(scheme, algo) else null
                }
            }
        }
    }

    @Test
    @Timeout(5)
    fun `Should require wrapping key`() {
        assertTrue(cryptoService.requiresWrappingKey())
    }

    @Test
    @Timeout(5)
    fun `All supported schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedSchemes().isNotEmpty())
        cryptoService.supportedSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(5)
    fun `All supported wrapping schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedWrappingSchemes().isNotEmpty())
        cryptoService.supportedWrappingSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(5)
    fun `containsKey should return false for unknown alias`() {
        val alias = newAlias()
        assertFalse(cryptoService.containsKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `containsKey should return true for known alias for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, signatureScheme)
        assertTrue(cryptoService.containsKey(alias))
    }

    @Test
    @Timeout(30)
    fun `findPublicKey should return null for unknown alias`() {
        val alias = newAlias()
        assertNull(cryptoService.findPublicKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `findPublicKey should return public key for known alias using for supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, signatureScheme)
        val publicKey = cryptoService.findPublicKey(alias)
        assertNotNull(publicKey)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should generate key pair and be able to sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        assertNotNull(publicKey)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
        val info = cryptoServiceCache.find(alias)
        assertNotNull(info)
        val signature = cryptoService.sign(alias, signatureScheme, testData)
        assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
    }


    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should fail to use key generated for another member for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        assertNotNull(publicKey)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
        val info = cryptoServiceCache.find(alias)
        assertNotNull(info)
        val signature = cryptoService.sign(alias, signatureScheme, testData)
        assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
        val otherMemberCryptoService = createCryptoServiceWithRandomMemberId()
        assertFailsWith<CryptoServiceBadRequestException> {
            otherMemberCryptoService.sign(alias, signatureScheme, testData)
        }
    }

    @ParameterizedTest
    @MethodSource("signatureSchemesWithPrecalculatedDigest")
    @Timeout(30)
    fun `Should generate key with all supported schemes and to sign and verify with precalculated digests`(
        signatureScheme: SignatureScheme,
        signatureSpec: SignatureSpec
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        assertNotNull(publicKey)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
        val info = cryptoServiceCache.find(alias)
        assertNotNull(info)
        val signature = cryptoService.sign(alias, signatureScheme, signatureSpec, testData)
        assertTrue(signatureSpec.precalculateHash)
        assertTrue(signatureVerifier.isValid(publicKey, signatureSpec, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signatureSpec, signature, badVerifyData))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should sign and verify several times with the same data and key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        for (i in 0..5) {
            assertNotNull(publicKey)
            val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
                "EdDSA"
            } else {
                signatureScheme.algorithmName
            }
            if (signatureScheme.algorithmName == "SPHINCS256") {
                assertEquals("SPHINCS-256", publicKey.algorithm)
            } else {
                assertEquals(expectedAlgo, publicKey.algorithm)
            }
            val info = cryptoServiceCache.find(alias)
            assertNotNull(info)
            val signature = cryptoService.sign(alias, signatureScheme, testData)
            assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
            assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should sign and verify several times using random data for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        val random = Random()
        for (i in 0..5) {
            val data = ByteArray(173)
            random.nextBytes(data)
            assertNotNull(publicKey)
            val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
                "EdDSA"
            } else {
                signatureScheme.algorithmName
            }
            if (signatureScheme.algorithmName == "SPHINCS256") {
                assertEquals("SPHINCS-256", publicKey.algorithm)
            } else {
                assertEquals(expectedAlgo, publicKey.algorithm)
            }
            val info = cryptoServiceCache.find(alias)
            assertNotNull(info)
            val signature = cryptoService.sign(alias, signatureScheme, data)
            assertTrue(signatureVerifier.isValid(publicKey, signature, data))
            assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should fail signing with unknown alias for all supported schemes`(signatureScheme: SignatureScheme) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        assertFailsWith<CryptoServiceException> {
            cryptoService.sign(alias, signatureScheme, testData)
        }
    }

    @Test
    @Timeout(30)
    fun `Should fail when generating key pair with unsupported signature scheme`() {
        val alias = newAlias()
        assertFailsWith<CryptoServiceBadRequestException> {
            cryptoService.generateKeyPair(alias, UNSUPPORTED_SIGNATURE_SCHEME)
        }
    }

    @Timeout(30)
    @ParameterizedTest
    @MethodSource("schemeAndAlgos")
    fun `Should generate, sign (using overload with algorithm name) and verify for all supported signature schemes`(
        signatureScheme: SignatureScheme,
        algoName: String
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val publicKey = cryptoService.generateKeyPair(alias, signatureScheme)
        val signature = cryptoService.sign(alias, signatureScheme, SignatureSpec(algoName), testData)
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        val res = verifyWithAlgo(signatureScheme, algoName, publicKey, signature, testData)
        assertTrue(res)
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should generate wrapped key pair and be able to use it to sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme)
        assertNotNull(wrappedKeyPair)
        assertNotNull(wrappedKeyPair.publicKey)
        assertNotNull(wrappedKeyPair.keyMaterial)
        val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            signatureScheme.algorithmName
        }
        if (signatureScheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", wrappedKeyPair.publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, wrappedKeyPair.publicKey.algorithm)
        }
        val signature = cryptoService.sign(
            WrappedPrivateKey(
                keyMaterial = wrappedKeyPair.keyMaterial,
                masterKeyAlias = wrappingKeyAlias,
                signatureScheme = signatureScheme,
                encodingVersion = wrappedKeyPair.encodingVersion
            ),
            signatureScheme.signatureSpec,
            testData
        )
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, badVerifyData))
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should generate and then sign and verify using wrapped key pair with different wrapping keys for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val wrappingKey2Alias = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(wrappingKey2Alias, true)
        val wrappedKeyPair1 = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme)
        val wrappedKeyPair2 = cryptoService.generateWrappedKeyPair(wrappingKey2Alias, signatureScheme)
        val signature1 = cryptoService.sign(
            WrappedPrivateKey(
                keyMaterial = wrappedKeyPair1.keyMaterial,
                masterKeyAlias = wrappingKeyAlias,
                signatureScheme = signatureScheme,
                encodingVersion = wrappedKeyPair1.encodingVersion
            ),
            signatureScheme.signatureSpec,
            testData
        )
        val signature2 = cryptoService.sign(
            WrappedPrivateKey(
                keyMaterial = wrappedKeyPair2.keyMaterial,
                masterKeyAlias = wrappingKey2Alias,
                signatureScheme = signatureScheme,
                encodingVersion = wrappedKeyPair2.encodingVersion
            ),
            signatureScheme.signatureSpec,
            testData
        )
        assertNotNull(signature1)
        assertNotNull(signature2)
        assertTrue(signature1.isNotEmpty())
        assertTrue(signature2.isNotEmpty())
        assertTrue(
            signatureVerifier.isValid(wrappedKeyPair1.publicKey, signature1, testData),
            "Verify failed for algorithm ${signatureScheme.algorithmName}"
        )
        assertTrue(
            signatureVerifier.isValid(wrappedKeyPair2.publicKey, signature2, testData),
            "Verify failed for algorithm ${signatureScheme.algorithmName}"
        )
        assertFailsWith<SignatureException> {
            signatureVerifier.verify(wrappedKeyPair2.publicKey, signature1, testData)
        }
        assertFailsWith<SignatureException> {
            signatureVerifier.verify(wrappedKeyPair1.publicKey, signature2, testData)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should generate and then sign and verify using wrapped key pair several times with same data for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val badVerifyData = UUID.randomUUID().toString().toByteArray()
        val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme)
        for (i in 0..5) {
            val signature = cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                signatureScheme.signatureSpec,
                testData
            )
            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
            assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, testData))
            assertFailsWith<SignatureException> {
                signatureVerifier.verify(wrappedKeyPair.publicKey, signature, badVerifyData)
            }
        }
    }
    
    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail generating wrapped key pair with unknown wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val wrappingKey2Alias = UUID.randomUUID().toString()
        assertFailsWith<CryptoServiceBadRequestException> {
            cryptoService.generateWrappedKeyPair(wrappingKey2Alias, signatureScheme)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail signing using wrapped key pair with unknown wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val wrappingKey2Alias = UUID.randomUUID().toString()
        val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme)
        assertFailsWith<CryptoServiceBadRequestException> {
            cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = wrappingKey2Alias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                signatureScheme.signatureSpec,
                testData
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail to verify using wrapped key pair with incorrect public key for all supported signature schemes`(
        signatureScheme: SignatureScheme
    ) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val wrappedKeyPair1 = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme)
        val wrappedKeyPair2 = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, signatureScheme)
        val signature = cryptoService.sign(
            WrappedPrivateKey(
                keyMaterial = wrappedKeyPair1.keyMaterial,
                masterKeyAlias = wrappingKeyAlias,
                signatureScheme = signatureScheme,
                encodingVersion = wrappedKeyPair1.encodingVersion
            ),
            signatureScheme.signatureSpec,
            testData
        )
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
        assertFailsWith<SignatureException> {
            signatureVerifier.verify(wrappedKeyPair2.publicKey, signature, testData)
        }
    }

    @Test
    @Timeout(30)
    fun `Should generate deterministic signatures for EdDSA, SPHINCS-256 and RSA`() {
        listOf(
            schemeMetadata.schemes.first { it.codeName == EDDSA_ED25519_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == SPHINCS256_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == RSA_CODE_NAME }
        ).forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            cryptoService.generateKeyPair(alias, signatureScheme)

            val signedData1stTime = cryptoService.sign(alias, signatureScheme, testData)
            val signedData2ndTime = cryptoService.sign(alias, signatureScheme, testData)
            assertEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))

            // Try for the special case of signing a zero array.
            val signedZeroArray1stTime = cryptoService.sign(alias, signatureScheme, test100ZeroBytes)
            val signedZeroArray2ndTime = cryptoService.sign(alias, signatureScheme, test100ZeroBytes)
            assertEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))

            // Just in case, test that signatures of different messages are not the same.
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
        }
    }

    @Test
    @Timeout(30)
    fun `Should generate non deterministic signatures for ECDSA`() {
        listOf(
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256K1_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        ).forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            cryptoService.generateKeyPair(alias, signatureScheme)

            val signedData1stTime = cryptoService.sign(alias, signatureScheme, testData)
            val signedData2ndTime = cryptoService.sign(alias, signatureScheme, testData)
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))

            // Try for the special case of signing a zero array.
            val signedZeroArray1stTime = cryptoService.sign(alias, signatureScheme, test100ZeroBytes)
            val signedZeroArray2ndTime = cryptoService.sign(alias, signatureScheme, test100ZeroBytes)
            assertNotEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))

            // Just in case, test that signatures of different messages are not the same.
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
        }
    }

    @Test
    @Timeout(30)
    fun `Should generate RSA key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "RSA")
        assertEquals(keyPair.public.algorithm, "RSA")
    }

    @Test
    @Timeout(30)
    fun `Should generate ECDSA key pair with secp256k1 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(ECDSA_SECP256K1_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "EC")
        assertEquals((keyPair.private as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
        assertEquals(keyPair.public.algorithm, "EC")
        assertEquals((keyPair.public as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
    }

    @Test
    @Timeout(30)
    fun `Should generate ECDSA key pair with secp256r1 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(ECDSA_SECP256R1_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "EC")
        assertEquals((keyPair.private as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
        assertEquals(keyPair.public.algorithm, "EC")
        assertEquals((keyPair.public as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
    }

    @Test
    @Timeout(30)
    fun `Should generate EdDSA key pair with ED25519 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(EDDSA_ED25519_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "EdDSA")
        assertEquals((keyPair.private as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals(keyPair.public.algorithm, "EdDSA")
        assertEquals((keyPair.public as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
    }

    @Test
    @Timeout(30)
    fun `Should generate SPHINCS-256 key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(SPHINCS256_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "SPHINCS-256")
        assertEquals(keyPair.public.algorithm, "SPHINCS-256")
    }

    @Test
    @Timeout(30)
    fun `Should generate SM2 key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(SM2_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "EC")
        assertEquals((keyPair.private as ECKey).parameters, ECNamedCurveTable.getParameterSpec("sm2p256v1"))
        assertEquals(keyPair.public.algorithm, "EC")
        assertEquals((keyPair.public as ECKey).parameters, ECNamedCurveTable.getParameterSpec("sm2p256v1"))
    }

    @Test
    @Timeout(30)
    fun `Should generate GOST3410_GOST3411 key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(GOST3410_GOST3411_CODE_NAME)
        cryptoService.generateKeyPair(alias, scheme)
        val keyPair = getGeneratedKeyPair(alias)
        assertEquals(keyPair.private.algorithm, "GOST3410")
        assertEquals(keyPair.public.algorithm, "GOST3410")
    }

    private fun createCryptoServiceWithRandomMemberId(): CryptoService {
        val cache = DefaultCryptoKeyCacheImpl(
            memberId = UUID.randomUUID().toString(),
            passphrase = "PASSPHRASE",
            salt = "SALT",
            schemeMetadata = cryptoMocks.schemeMetadata,
            persistence = cryptoMocks.defaultPersistentKeyCache
        )
        return DefaultCryptoService(
            cache = cache,
            schemeMetadata = schemeMetadata,
            hashingService = cryptoMocks.factories.cryptoLibrary.getDigestService()
        )
    }

    private fun getGeneratedKeyPair(alias: String): KeyPair {
        val record = cryptoServiceCache.find(alias)
        return KeyPair(record!!.publicKey, record.privateKey)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun verifyWithAlgo(
        scheme: SignatureScheme,
        signAlgo: String,
        publicKey: PublicKey,
        signatureData: ByteArray,
        clearData: ByteArray
    ): Boolean {
        val sig = Signature.getInstance(signAlgo, schemeMetadata.providers[scheme.providerName])
        sig.initVerify(publicKey)
        sig.update(clearData)
        return sig.verify(signatureData)
    }
}
