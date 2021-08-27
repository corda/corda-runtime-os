package net.corda.impl.cipher.suite

import net.corda.cipher.suite.impl.CipherSchemeMetadataProviderImpl
import net.corda.cipher.suite.impl.CryptoServiceCircuitBreaker
import net.corda.cipher.suite.impl.DefaultCachedKey
import net.corda.cipher.suite.impl.DefaultCryptoPersistentKey
import net.corda.cipher.suite.impl.DefaultCryptoService
import net.corda.cipher.suite.impl.DefaultKeyCacheImpl
import net.corda.crypto.testkit.CryptoMocks
import net.corda.crypto.impl.caching.SimplePersistentCacheFactory
import net.corda.crypto.impl.caching.SimplePersistentCacheImpl
import net.corda.impl.test.MockDatabaseBuilder
import net.corda.crypto.SignatureVerificationServiceInternal
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.schemes.NaSignatureSpec
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPair
import java.security.PublicKey
import java.security.Signature
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        lateinit var sessionFactory: SessionFactory
        lateinit var basicKeyCache: DefaultKeyCacheImpl
        lateinit var signatureVerifier: SignatureVerificationServiceInternal
        lateinit var schemeMetadata: CipherSchemeMetadata
        lateinit var cryptoService: CryptoService

        @JvmStatic
        fun supportedSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedSchemes()
        }

        @JvmStatic
        fun supportedWrappingSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedWrappingSchemes()
        }

        @JvmStatic
        fun customSignatureSchemes(): List<Arguments> =
                schemeMetadata.digests.flatMap { digest ->
                    cryptoService.supportedSchemes().filter { scheme ->
                        scheme.algorithmName == "RSA" || scheme.algorithmName == "EC"
                    }.map { scheme ->
                        when (scheme.algorithmName) {
                            "RSA" -> Arguments.of(scheme, SignatureSpec(
                                    signatureName = "RSA/NONE/PKCS1Padding",
                                    customDigestName = DigestAlgorithmName(digest.algorithmName)
                            ))
                            "EC" -> Arguments.of(scheme, SignatureSpec(
                                    signatureName = "NONEwithECDSA",
                                    customDigestName = DigestAlgorithmName(digest.algorithmName)
                            ))
                            else -> Arguments.of(scheme, SignatureSpec(
                                    signatureName = "NONEwith${scheme.algorithmName}",
                                    customDigestName = DigestAlgorithmName(digest.algorithmName)
                            ))
                        }
                    }
                }.toList()

        @JvmStatic
        fun schemeAndAlgos(): List<Arguments> {
            return cryptoService.supportedSchemes().flatMap { scheme: SignatureScheme ->
                // Run only for the schemeAlgoName passed in. EC has multiple schemes for different curves, so run for all schemes that this algorithm applies to
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

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataProviderImpl().getInstance()
            val cryptoMocks = CryptoMocks(schemeMetadataImpl = schemeMetadata)
            signatureVerifier = cryptoMocks.signatureVerificationService()
            sessionFactory = MockDatabaseBuilder.resetDatabase()
            val cache = SimplePersistentCacheImpl<DefaultCachedKey, DefaultCryptoPersistentKey>(
                    DefaultCryptoPersistentKey::class.java,
                    sessionFactory
            )
            basicKeyCache = DefaultKeyCacheImpl(
                    sandboxId = "BasicCryptoServiceTests-s111",
                    partition = "BasicCryptoServiceTests-p111",
                    passphrase = "passphrase-111",
                    salt = "11",
                    cacheFactory = object : SimplePersistentCacheFactory<DefaultCachedKey, DefaultCryptoPersistentKey> {
                        override fun create() = cache
                    },
                    schemeMetadata = schemeMetadata
            )
            cryptoService = CryptoServiceCircuitBreaker(
                    cryptoService = DefaultCryptoService(
                            cache = basicKeyCache,
                            schemeMetadata = schemeMetadata,
                            hashingService = cryptoMocks.digestService()
                    ),
                    timeout = Duration.ofSeconds(5)
            )
            cryptoService.createWrappingKey(wrappingKeyAlias, true)
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            sessionFactory.close()
        }
    }

    @Test
    @Timeout(30)
    fun `Should require wrapping key`() {
        assertTrue(cryptoService.requiresWrappingKey())
    }

    @Test
    @Timeout(30)
    fun `All supported schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedSchemes().isNotEmpty())
        cryptoService.supportedSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(30)
    fun `All supported wraping schemes should be defined in cipher suite`() {
        assertTrue(cryptoService.supportedWrappingSchemes().isNotEmpty())
        cryptoService.supportedWrappingSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(30)
    fun `containsKey should return false for unknown alias`() {
        val alias = newAlias()
        assertFalse(cryptoService.containsKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `containsKey should return true for known alias`(scheme: SignatureScheme) {
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, scheme)
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
    fun `findPublicKey should return public key for known alias`(scheme: SignatureScheme) {
        val alias = newAlias()
        cryptoService.generateKeyPair(alias, scheme)
        val publicKey = cryptoService.findPublicKey(alias)
        assertNotNull(publicKey)
        val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
            "EdDSA"
        } else {
            scheme.algorithmName
        }
        if (scheme.algorithmName == "SPHINCS256") {
            assertEquals("SPHINCS-256", publicKey.algorithm)
        } else {
            assertEquals(expectedAlgo, publicKey.algorithm)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should generate key pair with all supported schemes and be able to sign and verify using default signature scheme`(signatureScheme: SignatureScheme) {
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
        assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
        val signature = cryptoService.sign(alias, signatureScheme, testData)
        assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
    }

    @ParameterizedTest
    @MethodSource("customSignatureSchemes")
    @Timeout(300)
    fun `Should generate key pair with all supported schemes and be able to sign and verify using custom signature scheme`(
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
        assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
        val signature = cryptoService.sign(alias, signatureScheme, signatureSpec, testData)
        assertTrue(signatureSpec.precalculateHash)
        assertTrue(signatureVerifier.isValid(publicKey, signatureSpec, signature, testData))
        assertFalse(signatureVerifier.isValid(publicKey, signatureSpec, signature, badVerifyData))
    }

    /*
        @ParameterizedTest
        @MethodSource("supportedSchemes")
        @Timeout(30)
        fun `Should sign and verify several times using the same data`(scheme: KeyScheme) {
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, scheme)
            for (i in 0..5) {
                assertNotNull(publicKey)
                val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
                    "EdDSA"
                } else {
                    scheme.algorithmName
                }
                if (scheme.algorithmName == "SPHINCS256") {
                    assertEquals("SPHINCS-256", publicKey.algorithm)
                } else {
                    assertEquals(expectedAlgo, publicKey.algorithm)
                }
                assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
                val signature = cryptoService.sign(alias, testData, scheme)
                assertTrue(signatureVerifier.isValid(publicKey, signature, testData))
                assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
            }
        }

        @ParameterizedTest
        @MethodSource("supportedSchemes")
        @Timeout(30)
        fun `Should sign and verify several times using the random data`(scheme: KeyScheme) {
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, scheme)
            val random = Random()
            for (i in 0..5) {
                val data = ByteArray(173)
                random.nextBytes(data)
                assertNotNull(publicKey)
                val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
                    "EdDSA"
                } else {
                    scheme.algorithmName
                }
                if (scheme.algorithmName == "SPHINCS256") {
                    assertEquals("SPHINCS-256", publicKey.algorithm)
                } else {
                    assertEquals(expectedAlgo, publicKey.algorithm)
                }
                assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
                val signature = cryptoService.sign(alias, data, scheme)
                assertTrue(signatureVerifier.isValid(publicKey, signature, data))
                assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
            }
        }

        @ParameterizedTest
        @MethodSource("supportedSchemes")
        @Timeout(30)
        fun `Should generate, sign and verify several times`(scheme: KeyScheme) {
            val random = Random()
            for (i in 0..5) {
                val alias = newAlias()
                val publicKey = cryptoService.generateKeyPair(alias, scheme)
                val data = ByteArray(173)
                random.nextBytes(data)
                assertNotNull(publicKey)
                val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
                    "EdDSA"
                } else {
                    scheme.algorithmName
                }
                if (scheme.algorithmName == "SPHINCS256") {
                    assertEquals("SPHINCS-256", publicKey.algorithm)
                } else {
                    assertEquals(expectedAlgo, publicKey.algorithm)
                }
                assertTrue(getAllRows().map { it.alias }.any { it.endsWith(":$alias") })
                val signature = cryptoService.sign(alias, data, scheme)
                assertTrue(signatureVerifier.isValid(publicKey, signature, data))
                assertFalse(signatureVerifier.isValid(publicKey, signature, badVerifyData))
            }
        }

        @Test
        @Timeout(30)
        fun `Should fail signing with unknown alias`() {
            val alias = newAlias()
            assertFailsWith<CryptoServiceException> {
                cryptoService.sign(alias, testData, cryptoService.supportedSchemes().first())
            }
        }

        @Timeout(30)
        @ParameterizedTest
        @MethodSource("schemeAndAlgos")
        fun `Should fail verification when signing with algorithm with bad name`(scheme: KeyScheme, algoName: String) {
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, scheme)
            val signature = cryptoService.sign(alias, testData, scheme, algoName)
            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
            verifyWithAlgo(scheme, algoName, publicKey, signature, testData)
            assertFailsWith<NoSuchAlgorithmException> {
                verifyWithAlgo(scheme, algoName + "X", publicKey, signature, testData)
            }
        }

        @Timeout(30)
        @ParameterizedTest
        @MethodSource("schemeAndAlgos")
        fun `Should fail to verify with different data`(scheme: KeyScheme, algoName: String) {
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, scheme)
            val signature = cryptoService.sign(alias, testData, scheme, algoName)
            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
            val res = verifyWithAlgo(scheme, algoName, publicKey, signature, badVerifyData)
            assertFalse(res)
        }

        @Timeout(30)
        @ParameterizedTest
        @MethodSource("schemeAndAlgos")
        fun `Should generate, sign and verify for all supported signature algorithms`(scheme: KeyScheme, algoName: String) {
            val alias = newAlias()
            val publicKey = cryptoService.generateKeyPair(alias, scheme)
            val signature = cryptoService.sign(alias, testData, scheme, algoName)
            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
            val res = verifyWithAlgo(scheme, algoName, publicKey, signature, testData)
            assertTrue(res)
        }

        @Test
        @Timeout(30)
        fun `Should fail when generating key pair with unsupported signature scheme`() {
            val alias = newAlias()
            assertFailsWith<CryptoServiceBadRequestException> {
                cryptoService.generateKeyPair(alias, UNSUPPORTED_SIGNATURE_SCHEME)
            }
        }

        @ParameterizedTest
        @MethodSource("supportedWrappingSchemes")
        @Timeout(30)
        fun `Should generate wrapped key pair with all supported schemes and be able to use it to sign and verify`(scheme: KeyScheme) {
            val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            assertNotNull(wrappedKeyPair)
            assertNotNull(wrappedKeyPair.publicKey)
            assertNotNull(wrappedKeyPair.keyMaterial)
            val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
                "EdDSA"
            } else {
                scheme.algorithmName
            }
            if(scheme.algorithmName == "SPHINCS256") {
                assertEquals("SPHINCS-256", wrappedKeyPair.publicKey.algorithm)
            } else {
                assertEquals(expectedAlgo, wrappedKeyPair.publicKey.algorithm)
            }
            val signature = cryptoService.sign(
                    WrappedPrivateKey(
                            keyMaterial = wrappedKeyPair.keyMaterial,
                            masterKeyAlias = wrappingKeyAlias,
                            keyScheme = scheme,
                            encodingVersion = wrappedKeyPair.encodingVersion
                    ),
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
        fun `Should sign and verify using wrapped key pair with different wrapping keys`(scheme: KeyScheme) {
            val wrappingKey2Alias = UUID.randomUUID().toString()
            cryptoService.createWrappingKey(wrappingKey2Alias, true)
            val wrappedKeyPair1 = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            val wrappedKeyPair2 = cryptoService.generateWrappedKeyPair(wrappingKey2Alias, scheme)
            val signature1 = cryptoService.sign(
                    WrappedPrivateKey(
                            keyMaterial = wrappedKeyPair1.keyMaterial,
                            masterKeyAlias = wrappingKeyAlias,
                            keyScheme = scheme,
                            encodingVersion = wrappedKeyPair1.encodingVersion
                    ),
                    testData
            )
            val signature2 = cryptoService.sign(
                    WrappedPrivateKey(
                            keyMaterial = wrappedKeyPair2.keyMaterial,
                            masterKeyAlias = wrappingKey2Alias,
                            keyScheme = scheme,
                            encodingVersion = wrappedKeyPair2.encodingVersion
                    ),
                    testData
            )
            assertNotNull(signature1)
            assertNotNull(signature2)
            assertTrue(signature1.isNotEmpty())
            assertTrue(signature2.isNotEmpty())
            assertTrue(
                    signatureVerifier.isValid(wrappedKeyPair1.publicKey, signature1, testData),
                    "Verify failed for algorithm ${scheme.algorithmName}"
            )
            assertTrue(
                    signatureVerifier.isValid(wrappedKeyPair2.publicKey, signature2, testData),
                    "Verify failed for algorithm ${scheme.algorithmName}"
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
        fun `Should fail generating wrapped key pair with unknown wrapping key`(scheme: KeyScheme) {
            val wrappingKey2Alias = UUID.randomUUID().toString()
            assertFailsWith<CryptoServiceBadRequestException> {
                cryptoService.generateWrappedKeyPair(wrappingKey2Alias, scheme)
            }
        }

        @ParameterizedTest
        @MethodSource("supportedWrappingSchemes")
        @Timeout(30)
        fun `Should fail signing using wrapped key pair with unknown wrapping key`(scheme: KeyScheme) {
            val wrappingKey2Alias = UUID.randomUUID().toString()
            val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            assertFailsWith<CryptoServiceBadRequestException> {
                cryptoService.sign(
                        WrappedPrivateKey(
                                keyMaterial = wrappedKeyPair.keyMaterial,
                                masterKeyAlias = wrappingKey2Alias,
                                keyScheme = scheme,
                                encodingVersion = wrappedKeyPair.encodingVersion
                        ),
                        testData
                )
            }
        }

        @ParameterizedTest
        @MethodSource("supportedWrappingSchemes")
        @Timeout(30)
        fun `Should sign and verify using wrapped key pair several times with same data`(scheme: KeyScheme) {
            val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            for (i in 0..5) {
                val signature = cryptoService.sign(
                        WrappedPrivateKey(
                                keyMaterial = wrappedKeyPair.keyMaterial,
                                masterKeyAlias = wrappingKeyAlias,
                                keyScheme = scheme,
                                encodingVersion = wrappedKeyPair.encodingVersion
                        ),
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
        fun `Should sign and verify using wrapped key pair several times with different data`(scheme: KeyScheme) {
            val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            val random = Random()
            for (i in 0..5) {
                val data = ByteArray(173)
                random.nextBytes(data)
                val signature = cryptoService.sign(
                        WrappedPrivateKey(
                                keyMaterial = wrappedKeyPair.keyMaterial,
                                masterKeyAlias = wrappingKeyAlias,
                                keyScheme = scheme,
                                encodingVersion = wrappedKeyPair.encodingVersion
                        ),
                        data
                )
                assertNotNull(signature)
                assertTrue(signature.isNotEmpty())
                assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, data))
                assertFailsWith<SignatureException> {
                    signatureVerifier.verify(wrappedKeyPair.publicKey, signature, badVerifyData)
                }
            }
        }

        @ParameterizedTest
        @MethodSource("supportedWrappingSchemes")
        @Timeout(30)
        fun `Should generate, sign and verify using wrapped key pair several times`(scheme: KeyScheme) {
            val random = Random()
            for (i in 0..5) {
                val wrappedKeyPair = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
                val data = ByteArray(173)
                random.nextBytes(data)
                val signature = cryptoService.sign(
                        WrappedPrivateKey(
                                keyMaterial = wrappedKeyPair.keyMaterial,
                                masterKeyAlias = wrappingKeyAlias,
                                keyScheme = scheme,
                                encodingVersion = wrappedKeyPair.encodingVersion
                        ),
                        data
                )
                assertNotNull(signature)
                assertTrue(signature.isNotEmpty())
                assertTrue(signatureVerifier.isValid(wrappedKeyPair.publicKey, signature, data))
                assertFailsWith<SignatureException> {
                    signatureVerifier.verify(wrappedKeyPair.publicKey, signature, badVerifyData)
                }
            }
        }

        @ParameterizedTest
        @MethodSource("supportedWrappingSchemes")
        @Timeout(30)
        fun `Should fail to sign and verify using wrapped key pair with incorrect public key for all valid signature schemes`(scheme: KeyScheme) {
            val wrappedKeyPair1 = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            val wrappedKeyPair2 = cryptoService.generateWrappedKeyPair(wrappingKeyAlias, scheme)
            val signature = cryptoService.sign(
                    WrappedPrivateKey(
                            keyMaterial = wrappedKeyPair1.keyMaterial,
                            masterKeyAlias = wrappingKeyAlias,
                            keyScheme = scheme,
                            encodingVersion = wrappedKeyPair1.encodingVersion
                    ),
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
        fun `Signatures of EdDSA, SPHINCS-256 and RSA PKCS1 should be deterministic`() {
            listOf(
                    schemeMetadata.keySchemes.first { it.codeName == EDDSA_ED25519_CODE_NAME },
                    schemeMetadata.keySchemes.first { it.codeName == SPHINCS256_CODE_NAME },
                    schemeMetadata.keySchemes.first { it.codeName == RSA_CODE_NAME }
            ).forEach { scheme ->
                val alias = newAlias()
                cryptoService.generateKeyPair(alias, scheme)

                val signedData1stTime = cryptoService.sign(alias, testData, scheme)
                val signedData2ndTime = cryptoService.sign(alias, testData, scheme)
                assertEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))

                // Try for the special case of signing a zero array.
                val signedZeroArray1stTime = cryptoService.sign(alias, test100ZeroBytes, scheme)
                val signedZeroArray2ndTime = cryptoService.sign(alias, test100ZeroBytes, scheme)
                assertEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))

                // Just in case, test that signatures of different messages are not the same.
                assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
            }
        }

        @Test
        @Timeout(30)
        fun `Signatures of ECDSA should not be deterministic`() {
            listOf(
                    schemeMetadata.schemes.first { it.schemeCodeName == ECDSA_SECP256K1_CODE_NAME },
                    schemeMetadata.schemes.first { it.schemeCodeName == ECDSA_SECP256R1_CODE_NAME }
            ).forEach { scheme ->
                val alias = newAlias()
                cryptoService.generateKeyPair(alias, scheme)

                val signedData1stTime = cryptoService.sign(alias, testData, scheme)
                val signedData2ndTime = cryptoService.sign(alias, testData, scheme)
                assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))

                // Try for the special case of signing a zero array.
                val signedZeroArray1stTime = cryptoService.sign(alias, test100ZeroBytes, scheme)
                val signedZeroArray2ndTime = cryptoService.sign(alias, test100ZeroBytes, scheme)
                assertNotEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))

                // Just in case, test that signatures of different messages are not the same.
                assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
            }
        }

        @Test
        @Timeout(30)
        fun `Should generate RSA key pair`() {
            val alias = newAlias()
            val scheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
            cryptoService.generateKeyPair(alias, scheme)
            val keyPair = getGeneratedKeyPair(alias)
            assertEquals(keyPair.private .algorithm, "RSA")
            assertEquals(keyPair.public.algorithm, "RSA")
        }

        @Test
        @Timeout(30)
        fun `Should generate ECDSA key pair with secp256k1 curve`() {
            val alias = newAlias()
            val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256K1_CODE_NAME)
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
            val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
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
            val scheme = schemeMetadata.findKeyScheme(EDDSA_ED25519_CODE_NAME)
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
            val scheme = schemeMetadata.findKeyScheme(SPHINCS256_CODE_NAME)
            cryptoService.generateKeyPair(alias, scheme)
            val keyPair = getGeneratedKeyPair(alias)
            assertEquals(keyPair.private.algorithm, "SPHINCS-256")
            assertEquals(keyPair.public.algorithm, "SPHINCS-256")
        }
    */
    private fun getGeneratedKeyPair(alias: String): KeyPair {
        val record = net.corda.impl.cipher.suite.DefaultCryptoServiceTests.Companion.basicKeyCache.find(alias)
        return KeyPair(record!!.publicKey, record.privateKey)
    }

    private fun newAlias(): String = UUID.randomUUID().toString()

    private fun getAllRows(): List<DefaultCryptoPersistentKey> {
        return net.corda.impl.cipher.suite.DefaultCryptoServiceTests.Companion.sessionFactory.openSession().use { session ->
            val cb = session.criteriaBuilder
            val cr = cb.createQuery(DefaultCryptoPersistentKey::class.java)
            val root = cr.from(DefaultCryptoPersistentKey::class.java)
            cr.select(root)
            val query = session.createQuery(cr)
            query.resultList
        }
    }

    private fun verifyWithAlgo(
            scheme: SignatureScheme,
            signAlgo: String,
            publicKey: PublicKey,
            signatureData: ByteArray,
            clearData: ByteArray
    ): Boolean {
        val sig = Signature.getInstance(signAlgo, net.corda.impl.cipher.suite.DefaultCryptoServiceTests.Companion.schemeMetadata.providers[scheme.providerName])
        sig.initVerify(publicKey)
        sig.update(clearData)
        return sig.verify(signatureData)
    }
}
