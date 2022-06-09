package net.corda.crypto.service.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.service.impl.infra.generateKeyPair
import net.corda.test.util.createTestCase
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.CustomSignatureSpec
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SignatureVerificationService
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.RSASSA_PSS_SHA256_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests are combined to improve performance as it takes a lot of time to generate keys and considering the number
 * of permutations when especially running tests for customized signature specs (over 70) it makes sense
 * trying to generate keys once and run all related tests
 */
class CryptoOperationsTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var verifier: SignatureVerificationService
        private lateinit var tenantId: String
        private lateinit var category: String
        private lateinit var wrappingKeyAlias: String
        private lateinit var cryptoService: CryptoService

        private val zeroBytes = ByteArray(100)

        private val UNSUPPORTED_KEY_SCHEME = KeyScheme(
            codeName = "UNSUPPORTED_SIGNATURE_SCHEME",
            algorithmOIDs = listOf(
                AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER)
            ),
            providerName = "SUN",
            algorithmName = CompositeKey.KEY_ALGORITHM,
            algSpec = null,
            keySize = null
        )

        private lateinit var softAliasedKeys: Map<KeyScheme, GeneratedWrappedKey>

        private lateinit var softFreshKeys: Map<KeyScheme, GeneratedWrappedKey>

        class SigningAliasedKeyInfo(
            val alias: String,
            val publicKey: PublicKey,
            val signingService: SigningService
        )

        class SigningFreshKeyInfo(
            val externalId: String?,
            val publicKey: PublicKey,
            val signingService: SigningService
        )

        private lateinit var factory: TestServicesFactory
        private lateinit var signingAliasedKeys: Map<KeyScheme, SigningAliasedKeyInfo>
        private lateinit var signingFreshKeys: Map<KeyScheme, SigningFreshKeyInfo>
        private lateinit var signingFreshKeysWithoutExternalId: Map<KeyScheme, SigningFreshKeyInfo>
        private lateinit var unknownKeyPairs: Map<KeyScheme, KeyPair>

        @JvmStatic
        @BeforeAll
        fun setup() {
            factory = TestServicesFactory()
            schemeMetadata = factory.schemeMetadata
            verifier = factory.verifier
            tenantId = UUID.randomUUID().toString()
            category = CryptoConsts.Categories.LEDGER
            wrappingKeyAlias = factory.wrappingKeyAlias
            cryptoService = factory.cryptoService
            softAliasedKeys = supportedSchemes().associateWith {
                cryptoService.generateKeyPair(
                    KeyGenerationSpec(
                        keyScheme = it,
                        alias = UUID.randomUUID().toString(),
                        masterKeyAlias = wrappingKeyAlias,
                        secret = null
                    ),
                    mapOf(
                        CRYPTO_TENANT_ID to tenantId,
                        CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                    )
                ) as GeneratedWrappedKey
            }
            softFreshKeys = supportedSchemes().associateWith {
                cryptoService.generateKeyPair(
                    KeyGenerationSpec(
                        keyScheme = it,
                        alias = null,
                        masterKeyAlias = wrappingKeyAlias,
                        secret = null
                    ),
                    mapOf(
                        CRYPTO_TENANT_ID to tenantId,
                        CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                    )
                ) as GeneratedWrappedKey
            }
            signingAliasedKeys = supportedSchemes().associateWith {
                val signingService = factory.createSigningService()
                val alias = UUID.randomUUID().toString()
                SigningAliasedKeyInfo(
                    alias = alias,
                    signingService = signingService,
                    publicKey = signingService.generateKeyPair(
                        tenantId = tenantId,
                        category = CryptoConsts.Categories.LEDGER,
                        alias = alias,
                        scheme = it
                    )
                )
            }
            signingFreshKeys = supportedSchemes().associateWith {
                val signingService = factory.createSigningService()
                val externalId = UUID.randomUUID().toString()
                SigningFreshKeyInfo(
                    externalId = externalId,
                    signingService = signingService,
                    publicKey = signingService.freshKey(
                        tenantId = tenantId,
                        category = CryptoConsts.Categories.CI,
                        externalId = externalId,
                        scheme = it
                    )
                )
            }
            signingFreshKeysWithoutExternalId = supportedSchemes().associateWith {
                val signingService = factory.createSigningService()
                SigningFreshKeyInfo(
                    externalId = null,
                    signingService = signingService,
                    publicKey = signingService.freshKey(
                        tenantId = tenantId,
                        category = CryptoConsts.Categories.CI,
                        scheme = it
                    )
                )
            }
            unknownKeyPairs = supportedSchemes().associateWith {
                generateKeyPair(schemeMetadata, it.codeName)
            }
        }

        @JvmStatic
        fun supportedSchemes(): List<KeyScheme> =
            cryptoService.supportedSchemes.keys.toList()

        private fun getInferableDigestNames(scheme: KeyScheme): List<DigestAlgorithmName> =
            schemeMetadata.inferableDigestNames(scheme)

        private fun getAllStandardSignatureSpecs(scheme: KeyScheme): List<SignatureSpec> =
            schemeMetadata.supportedSignatureSpec(scheme)

        private fun getAllCustomSignatureSpecs(scheme: KeyScheme): List<SignatureSpec> =
            schemeMetadata.digests.mapNotNull { digest ->
                when (scheme.codeName) {
                    RSA_CODE_NAME -> CustomSignatureSpec(
                        signatureName = "RSA/NONE/PKCS1Padding",
                        customDigestName = DigestAlgorithmName(digest.algorithmName)
                    )
                    ECDSA_SECP256R1_CODE_NAME -> CustomSignatureSpec(
                        signatureName = "NONEwithECDSA",
                        customDigestName = DigestAlgorithmName(digest.algorithmName)
                    )
                    else -> null
                }
            }

        private fun verifyCachedKeyRecord(
            publicKey: PublicKey,
            alias: String?,
            uuid: String?,
            scheme: KeyScheme
        ) {
            val generatedKeyData = factory.getSigningCachedKey(tenantId, publicKey)
            assertNotNull(generatedKeyData)
            assertEquals(tenantId, generatedKeyData.tenantId)
            if (generatedKeyData.alias == null) {
                assertEquals(CryptoConsts.Categories.CI, generatedKeyData.category)
            } else {
                assertEquals(category, generatedKeyData.category)
            }
            assertEquals(uuid, generatedKeyData.externalId)
            assertArrayEquals(publicKey.encoded, generatedKeyData.publicKey)
            assertNull(generatedKeyData.hsmAlias)
            assertEquals(alias, generatedKeyData.alias)
            assertNotNull(generatedKeyData.keyMaterial)
            assertEquals(scheme.codeName, generatedKeyData.schemeCodeName)
            assertEquals(1, generatedKeyData.encodingVersion)
        }

        private fun verifySigningKeyInfo(
            publicKey: PublicKey,
            alias: String?,
            scheme: KeyScheme,
            key: SigningKeyInfo
        ) {
            assertEquals(alias, key.alias)
            assertNull(key.hsmAlias)
            if (key.alias == null) {
                assertEquals(CryptoConsts.Categories.CI, key.category)
            } else {
                assertEquals(category, key.category)
            }
            assertEquals(scheme.codeName, key.schemeCodeName)
            assertEquals(wrappingKeyAlias, key.masterKeyAlias)
            assertEquals(1, key.encodingVersion)
            assertArrayEquals(publicKey.encoded, key.publicKey)
        }

        private fun signAndValidateSignatureByInferringSignatureSpec(
            signingService: SigningService,
            publicKey: PublicKey
        ) {
            val scheme = schemeMetadata.findKeyScheme(publicKey)
            getInferableDigestNames(scheme).createTestCase { digest ->
                val badData = UUID.randomUUID().toString().toByteArray()
                val data = UUID.randomUUID().toString().toByteArray()
                val spec = schemeMetadata.inferSignatureSpec(publicKey, digest)
                assertNotNull(spec)
                val signature = signingService.sign(tenantId, publicKey, spec, data)
                assertEquals(publicKey, signature.by)
                assertTrue(
                    verifier.isValid(publicKey, digest, signature.bytes, data)
                )
                verifier.verify(publicKey, digest, signature.bytes, data)
                assertFalse(
                    verifier.isValid(publicKey, digest, signature.bytes, badData)
                )
                assertThrows<SignatureException> {
                    verifier.verify(publicKey, digest, signature.bytes, badData)
                }
                assertThrows<IllegalArgumentException> {
                    verifier.verify(publicKey, digest, signature.bytes, ByteArray(0))
                }
                assertThrows<IllegalArgumentException> {
                    verifier.verify(publicKey, digest, ByteArray(0), data)
                }
            }
        }

        private fun signAndValidateSignatureUsingExplicitSignatureSpec(
            signingService: SigningService,
            publicKey: PublicKey
        ) {
            val scheme = schemeMetadata.findKeyScheme(publicKey)
            (getAllStandardSignatureSpecs(scheme) + getAllCustomSignatureSpecs(scheme)).createTestCase { spec ->
                val data = UUID.randomUUID().toString().toByteArray()
                val signature = signingService.sign(tenantId, publicKey, spec, data)
                assertEquals(publicKey, signature.by)
                validateSignatureUsingExplicitSignatureSpec(publicKey, spec, signature.bytes, data)
            }.runAndValidate()
        }

        private fun validateSignatureUsingExplicitSignatureSpec(
            publicKey: PublicKey,
            signatureSpec: SignatureSpec,
            signature: ByteArray,
            data: ByteArray
        ) {
            val badData = UUID.randomUUID().toString().toByteArray()
            assertTrue(
                verifier.isValid(publicKey, signatureSpec, signature, data),
                "Should validate with ${signatureSpec.signatureName}"
            )
            verifier.verify(publicKey, signatureSpec, signature, data)
            assertFalse(
                verifier.isValid(publicKey, signatureSpec, signature, badData)
            )
            assertThrows<SignatureException> {
                verifier.verify(publicKey, signatureSpec, signature, badData)
            }
            assertThrows<IllegalArgumentException> {
                verifier.verify(publicKey, signatureSpec, signature, ByteArray(0))
            }
            assertThrows<IllegalArgumentException> {
                verifier.verify(publicKey, signatureSpec, ByteArray(0), data)
            }
        }

        private fun validatePublicKeyAlgorithm(
            scheme: KeyScheme,
            publicKey: PublicKey
        ) {
            val expectedAlgo = if (scheme.algorithmName == "1.3.101.112") {
                throw IllegalStateException("do we still need that?")
                //"EdDSA"
            } else {
                scheme.algorithmName
            }
            if (scheme.algorithmName == "SPHINCS256") {
                assertEquals("SPHINCS-256", publicKey.algorithm)
            } else {
                assertEquals(expectedAlgo, publicKey.algorithm)
            }
        }
    }

    @Test
    fun `SoftCryptoService should require wrapping key`() {
        assertThat(cryptoService.extensions).contains(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)
    }

    @Test
    fun `SoftCryptoService should not support key deletion`() {
        assertThat(cryptoService.extensions).doesNotContain(CryptoServiceExtensions.DELETE_KEYS)
    }

    @Test
    fun `SoftCryptoService should support at least one schemes defined in cipher suite`() {
        assertTrue(cryptoService.supportedSchemes.isNotEmpty())
        assertTrue(cryptoService.supportedSchemes.any {
            schemeMetadata.schemes.contains(it.key)
        })
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Suppress("MaxLineLength")
    fun `SoftCryptoService should throw IllegalStateException when signing with unknown wrapping key for all supported schemes`(
        scheme: KeyScheme
    ) {
        fun verifySign(key: GeneratedWrappedKey) {
            assertThrows<IllegalStateException> {
                cryptoService.sign(
                    SigningWrappedSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = UUID.randomUUID().toString(),
                        keyScheme = scheme,
                        signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first(),
                        encodingVersion = key.encodingVersion
                    ),
                    UUID.randomUUID().toString().toByteArray(),
                    mapOf(
                        CRYPTO_TENANT_ID to tenantId
                    )
                )
            }
        }
        verifySign(softAliasedKeys.getValue(scheme))
        verifySign(softFreshKeys.getValue(scheme))
    }

    @Test
    fun `SoftCryptoService should generate deterministic signatures for EdDSA, SPHINCS-256 and RSA`() {
        fun verifySign(key: GeneratedWrappedKey, scheme: KeyScheme) {
            val testData = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
            val signedData1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedData2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            assertArrayEquals(signedData1stTime, signedData2ndTime)
            val signedZeroArray1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedZeroArray2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            assertArrayEquals(signedZeroArray1stTime, signedZeroArray2ndTime)
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
        }
        listOf(
            schemeMetadata.schemes.first { it.codeName == EDDSA_ED25519_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == SPHINCS256_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == RSA_CODE_NAME }
        ).forEach { scheme ->
            verifySign(softAliasedKeys.getValue(scheme), scheme)
            verifySign(softFreshKeys.getValue(scheme), scheme)
        }
    }

    @Test
    fun `SoftCryptoService should generate non deterministic signatures for ECDSA`() {
        fun verifySign(key: GeneratedWrappedKey, scheme: KeyScheme) {
            val testData = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
            val signedData1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedData2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))
            val signedZeroArray1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedZeroArray2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    keyScheme = scheme,
                    signatureSpec = signatureSpec,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            assertNotEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))
        }
        listOf(
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256K1_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        ).forEach { scheme ->
            verifySign(softAliasedKeys.getValue(scheme), scheme)
            verifySign(softFreshKeys.getValue(scheme), scheme)
        }
    }

    @Test
    fun `SoftCryptoService should generate RSA key pair`() {
        val scheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        assertEquals("RSA", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("RSA", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should generate ECDSA key pair with secp256k1 curve`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("secp256k1"), (publicKey as ECKey).parameters)
        }

        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256K1_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(scheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(scheme).publicKey)
    }

    @Test
    fun `SoftCryptoService should generate ECDSA key pair with secp256r1 curve`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("secp256r1"), (publicKey as ECKey).parameters)
        }

        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(scheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(scheme).publicKey)
    }

    @Test
    fun `SoftCryptoService should generate EdDSA key pair with ED25519 curve`() {
        val scheme = schemeMetadata.findKeyScheme(EDDSA_ED25519_CODE_NAME)
        assertEquals("Ed25519", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("Ed25519", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should generate SPHINCS-256 key pair`() {
        val scheme = schemeMetadata.findKeyScheme(SPHINCS256_CODE_NAME)
        assertEquals("SPHINCS-256", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("SPHINCS-256", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should generate SM2 key pair`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("sm2p256v1"), (publicKey as ECKey).parameters)
        }

        val scheme = schemeMetadata.findKeyScheme(SM2_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(scheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(scheme).publicKey)
    }

    @Test
    fun `SoftCryptoService should generate GOST3410_GOST3411 key pair`() {
        val scheme = schemeMetadata.findKeyScheme(GOST3410_GOST3411_CODE_NAME)
        assertEquals("GOST3410", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("GOST3410", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should throw IllegalArgumentException when generating key pair with unsupported key scheme`() {
        assertThrows<IllegalArgumentException> {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = UNSUPPORTED_KEY_SCHEME,
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = wrappingKeyAlias,
                    secret = null
                ),
                mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
        assertThrows<IllegalArgumentException> {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = UNSUPPORTED_KEY_SCHEME,
                    alias = null,
                    masterKeyAlias = wrappingKeyAlias,
                    secret = null
                ),
                mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `SoftCryptoService should fail to use aliased key generated for another wrapping key for all supported schemes`(
        scheme: KeyScheme
    ) {
        val anotherWrappingKey = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(
            anotherWrappingKey,
            true,
            mapOf(
                CRYPTO_TENANT_ID to tenantId,
            )
        )
        val testData = UUID.randomUUID().toString().toByteArray()
        val key = softAliasedKeys.getValue(scheme)
        assertThrows<Throwable> {
            cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = anotherWrappingKey,
                    keyScheme = scheme,
                    signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first(),
                    encodingVersion = key.encodingVersion
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `SoftCryptoService should fail to use fresh key generated for another wrapping key for all supported schemes`(
        scheme: KeyScheme
    ) {
        val anotherWrappingKey = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(
            anotherWrappingKey,
            true, mapOf(
                CRYPTO_TENANT_ID to tenantId
            )
        )
        val testData = UUID.randomUUID().toString().toByteArray()
        val key = softFreshKeys.getValue(scheme)
        assertThrows<Throwable> {
            cryptoService.sign(
                SigningWrappedSpec(
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = anotherWrappingKey,
                    keyScheme = scheme,
                    signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first(),
                    encodingVersion = key.encodingVersion
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
        }
    }

    @Test
    fun `Should generate RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        val rsaPss = RSASSA_PSS_SHA256_SIGNATURE_SPEC
        val info = signingAliasedKeys.getValue(scheme)
        assertEquals(info.publicKey.algorithm, "RSA")
        val customSignature1 = info.signingService.sign(
            tenantId,
            info.publicKey,
            rsaPss,
            testData
        )
        assertEquals(info.publicKey, customSignature1.by)
        validateSignatureUsingExplicitSignatureSpec(info.publicKey, rsaPss, customSignature1.bytes, testData)
    }

    @Test
    fun `Should generate fresh RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        val rsaPss = RSASSA_PSS_SHA256_SIGNATURE_SPEC
        val info = signingFreshKeys.getValue(scheme)
        assertNotNull(info.publicKey)
        assertEquals(info.publicKey.algorithm, "RSA")
        val customSignature = info.signingService.sign(
            tenantId,
            info.publicKey,
            rsaPss,
            testData
        )
        assertEquals(info.publicKey, customSignature.by)
        validateSignatureUsingExplicitSignatureSpec(info.publicKey, rsaPss, customSignature.bytes, testData)
    }


    @Test
    fun `Filtering correctly our keys`() {
        val key1 = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val key2 = signingFreshKeys.values.first().publicKey
        val ourKeys = signingFreshKeys.values.first().signingService.lookup(
            tenantId,
            listOf(key1.publicKeyId(), key2.publicKeyId())
        ).toList()
        assertThat(ourKeys).hasSize(1)
        assertTrue(ourKeys.any { it.publicKey.contentEquals(key2.encoded) })
    }

    @Test
    fun `Filter our keys returns empty collection as none of the keys belong to us`() {
        val key1 = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val key2 = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val ourKeys = signingFreshKeys.values.first().signingService.lookup(
            tenantId,
            listOf(key1.publicKeyId(), key2.publicKeyId())
        ).toList()
        assertThat(ourKeys).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should lookup by id for aliased key in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned = info.signingService.lookup(tenantId, listOf(info.publicKey.publicKeyId()))
        assertEquals(1, returned.size)
        verifySigningKeyInfo(info.publicKey, info.alias, scheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should lookup by id for fresh key in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeys.getValue(scheme)
        val returned = info.signingService.lookup(tenantId, listOf(info.publicKey.publicKeyId()))
        assertEquals(1, returned.size)
        verifySigningKeyInfo(info.publicKey, null, scheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, null, info.externalId, scheme)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should return empty collection when looking up for not existing ids in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned = info.signingService.lookup(
            tenantId, listOf(publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray()))
        )
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should lookup for key in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned = info.signingService.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 50,
            orderBy = KeyOrderBy.ALIAS,
            mapOf(
                ALIAS_FILTER to info.alias
            )
        )
        assertEquals(1, returned.size)
        verifySigningKeyInfo(info.publicKey, info.alias, scheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should return empty collection when looking up for noy matching key parameters in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned = info.signingService.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 50,
            orderBy = KeyOrderBy.ALIAS,
            mapOf(
                ALIAS_FILTER to info.alias,
                MASTER_KEY_ALIAS_FILTER to UUID.randomUUID().toString()
            )
        )
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should not find public key when key pair hasn't been generated yet for all supported schemes`(
        scheme: KeyScheme
    ) {
        val unknownPublicKey = unknownKeyPairs.getValue(scheme).public
        val info = signingFreshKeys.getValue(scheme)
        val returned = info.signingService.lookup(tenantId, listOf(unknownPublicKey.publicKeyId()))
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should fail signing with unknown public key for all supported schemes`(scheme: KeyScheme) {
        val unknownPublicKey = unknownKeyPairs.getValue(scheme).public
        val info = signingFreshKeys.getValue(scheme)
        assertThrows<CryptoServiceException> {
            info.signingService.sign(
                tenantId = tenantId,
                publicKey = unknownPublicKey,
                signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first(),
                data = UUID.randomUUID().toString().toByteArray()
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should throw CryptoServiceException to sign for unknown tenant for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        assertThrows<CryptoServiceException> {
            info.signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = info.publicKey,
                signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first(),
                data = UUID.randomUUID().toString().toByteArray()
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should generate aliased keys and then sign and verify for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        signAndValidateSignatureByInferringSignatureSpec(info.signingService, info.publicKey)
        signAndValidateSignatureUsingExplicitSignatureSpec(info.signingService, info.publicKey)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should generate fresh keys and then sign and verify for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeys.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, null, info.externalId, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        signAndValidateSignatureByInferringSignatureSpec(info.signingService, info.publicKey)
        signAndValidateSignatureUsingExplicitSignatureSpec(info.signingService, info.publicKey)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should generate fresh keys without external id and then sign and verify for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeysWithoutExternalId.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, null, null, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        signAndValidateSignatureByInferringSignatureSpec(info.signingService, info.publicKey)
        signAndValidateSignatureUsingExplicitSignatureSpec(info.signingService, info.publicKey)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Signing service should use first known aliased key from CompositeKey when signing for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val bobPublicKey = info.publicKey
        verifyCachedKeyRecord(bobPublicKey, info.alias, null, scheme)
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
        val signature = info.signingService.sign(tenantId, aliceAndBob, signatureSpec, testData)
        assertEquals(bobPublicKey, signature.by)
        validateSignatureUsingExplicitSignatureSpec(signature.by, signatureSpec, signature.bytes, testData)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Signing service should use first known fresh key from CompositeKey when signing for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeys.getValue(scheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val bobPublicKey = info.publicKey
        verifyCachedKeyRecord(bobPublicKey, null, info.externalId, scheme)
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
        val signature = info.signingService.sign(tenantId, aliceAndBob, signatureSpec, testData)
        assertEquals(bobPublicKey, signature.by)
        validateSignatureUsingExplicitSignatureSpec(signature.by, signatureSpec, signature.bytes, testData)
    }
}

