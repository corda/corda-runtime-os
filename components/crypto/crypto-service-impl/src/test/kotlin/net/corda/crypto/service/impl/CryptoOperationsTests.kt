package net.corda.crypto.service.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.publicKeyIdOf
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.service.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.service.impl.infra.generateKeyPair
import net.corda.test.util.createTestCase
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.cipher.suite.schemes.NaSignatureSpec
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SM2_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SPHINCS256_CODE_NAME
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.collection.IsCollectionWithSize
import org.hamcrest.collection.IsEmptyCollection
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
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
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

        private val EMPTY_CONTEXT = emptyMap<String, String>()

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

        private lateinit var softAliasedKeys: Map<SignatureScheme, GeneratedWrappedKey>

        private lateinit var softFreshKeys: Map<SignatureScheme, GeneratedWrappedKey>

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
        private lateinit var signingAliasedKeys: Map<SignatureScheme, SigningAliasedKeyInfo>
        private lateinit var signingFreshKeys: Map<SignatureScheme, SigningFreshKeyInfo>
        private lateinit var signingFreshKeysWithoutExternalId: Map<SignatureScheme, SigningFreshKeyInfo>
        private lateinit var unknownKeyPairs: Map<SignatureScheme, KeyPair>

        @JvmStatic
        @BeforeAll
        fun setup() {
            factory = TestServicesFactory()
            schemeMetadata = factory.schemeMetadata
            verifier = factory.verifier
            tenantId = UUID.randomUUID().toString()
            category = CryptoConsts.HsmCategories.LEDGER
            wrappingKeyAlias = factory.wrappingKeyAlias
            cryptoService = factory.cryptoService
            softAliasedKeys = supportedSchemes().associateWith {
                cryptoService.generateKeyPair(
                    KeyGenerationSpec(
                        tenantId = tenantId,
                        signatureScheme = it,
                        alias = UUID.randomUUID().toString(),
                        masterKeyAlias = wrappingKeyAlias,
                        secret = null
                    ),
                    EMPTY_CONTEXT
                ) as GeneratedWrappedKey
            }
            softFreshKeys = supportedSchemes().associateWith {
                cryptoService.generateKeyPair(
                    KeyGenerationSpec(
                        tenantId = tenantId,
                        signatureScheme = it,
                        alias = null,
                        masterKeyAlias = wrappingKeyAlias,
                        secret = null
                    ),
                    EMPTY_CONTEXT
                ) as GeneratedWrappedKey
            }
            signingAliasedKeys = supportedSchemes().associateWith {
                val signingService = factory.createSigningService(it)
                val alias = UUID.randomUUID().toString()
                SigningAliasedKeyInfo(
                    alias = alias,
                    signingService = signingService,
                    publicKey = signingService.generateKeyPair(
                        tenantId = tenantId,
                        category = CryptoConsts.HsmCategories.LEDGER,
                        alias = alias
                    )
                )
            }
            signingFreshKeys = supportedSchemes().associateWith {
                val signingService = factory.createSigningService(it)
                val externalId = UUID.randomUUID().toString()
                SigningFreshKeyInfo(
                    externalId = externalId,
                    signingService = signingService,
                    publicKey = signingService.freshKey(
                        tenantId = tenantId,
                        externalId = externalId
                    )
                )
            }
            signingFreshKeysWithoutExternalId = supportedSchemes().associateWith {
                val signingService = factory.createSigningService(it)
                SigningFreshKeyInfo(
                    externalId = null,
                    signingService = signingService,
                    publicKey = signingService.freshKey(tenantId = tenantId)
                )
            }
            unknownKeyPairs = supportedSchemes().associateWith {
                generateKeyPair(schemeMetadata, it.codeName)
            }
        }

        @JvmStatic
        fun supportedSchemes(): Array<SignatureScheme> {
            return cryptoService.supportedSchemes()
        }

        fun getAllCustomSignatureSpecs(scheme: SignatureScheme): List<SignatureSpec> =
            if (scheme.codeName == RSA_CODE_NAME || scheme.codeName == ECDSA_SECP256R1_CODE_NAME) {
                schemeMetadata.digests.map { digest ->
                    when (scheme.algorithmName) {
                        "RSA" -> SignatureSpec(
                            signatureName = "RSA/NONE/PKCS1Padding",
                            customDigestName = DigestAlgorithmName(digest.algorithmName)
                        )
                        "EC" -> SignatureSpec(
                            signatureName = "NONEwithECDSA",
                            customDigestName = DigestAlgorithmName(digest.algorithmName)
                        )
                        else -> SignatureSpec(
                            signatureName = "NONEwith${scheme.algorithmName}",
                            customDigestName = DigestAlgorithmName(digest.algorithmName)
                        )
                    }
                }
            } else {
                emptyList()
            }

        private fun verifyCachedKeyRecord(
            publicKey: PublicKey,
            alias: String?,
            uuid: String?,
            signatureScheme: SignatureScheme
        ) {
            val generatedKeyData = factory.getSigningCachedKey(tenantId, publicKey)
            assertNotNull(generatedKeyData)
            assertEquals(tenantId, generatedKeyData.tenantId)
            if(generatedKeyData.alias == null) {
                assertEquals(CryptoConsts.HsmCategories.FRESH_KEYS, generatedKeyData.category)
            } else {
                assertEquals(category, generatedKeyData.category)
            }
            assertEquals(uuid, generatedKeyData.externalId)
            assertArrayEquals(publicKey.encoded, generatedKeyData.publicKey)
            assertNull(generatedKeyData.hsmAlias)
            assertEquals(alias, generatedKeyData.alias)
            assertNotNull(generatedKeyData.keyMaterial)
            assertEquals(signatureScheme.codeName, generatedKeyData.schemeCodeName)
            assertEquals(1, generatedKeyData.encodingVersion)
        }

        private fun verifySigningKeyInfo(
            publicKey: PublicKey,
            alias: String?,
            signatureScheme:
            SignatureScheme,
            key: SigningKeyInfo
        ) {
            assertEquals(alias, key.alias)
            assertNull(key.hsmAlias)
            if(key.alias == null) {
                assertEquals(CryptoConsts.HsmCategories.FRESH_KEYS, key.category)
            } else {
                assertEquals(category, key.category)
            }
            assertEquals(signatureScheme.codeName, key.schemeCodeName)
            assertEquals(wrappingKeyAlias, key.masterKeyAlias)
            assertEquals(1, key.encodingVersion)
            assertArrayEquals(publicKey.encoded, key.publicKey)
        }

        private fun validateSignature(
            publicKey: PublicKey,
            signature: ByteArray,
            data: ByteArray
        ) {
            val badData = UUID.randomUUID().toString().toByteArray()
            assertTrue(
                verifier.isValid(publicKey, signature, data)
            )
            verifier.verify(publicKey, signature, data)
            assertFalse(
                verifier.isValid(publicKey, signature, badData)
            )
            assertThrows<SignatureException> {
                verifier.verify(publicKey, signature, badData)
            }
            assertThrows<IllegalArgumentException> {
                verifier.verify(publicKey, signature, ByteArray(0))
            }
            assertThrows<IllegalArgumentException> {
                verifier.verify(publicKey, ByteArray(0), data)
            }
        }

        private fun validateSignature(
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
            signatureScheme: SignatureScheme,
            publicKey: PublicKey
        ) {
            val expectedAlgo = if (signatureScheme.algorithmName == "1.3.101.112") {
                throw IllegalStateException("do we still need that?")
                //"EdDSA"
            } else {
                signatureScheme.algorithmName
            }
            if (signatureScheme.algorithmName == "SPHINCS256") {
                assertEquals("SPHINCS-256", publicKey.algorithm)
            } else {
                assertEquals(expectedAlgo, publicKey.algorithm)
            }
        }
    }

    @Test
    fun `SoftCryptoService should require wrapping key`() {
        assertTrue(cryptoService.requiresWrappingKey())
    }

    @Test
    fun `SoftCryptoService should support only schemes defined in cipher suite`() {
        assertTrue(cryptoService.supportedSchemes().isNotEmpty())
        cryptoService.supportedSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `SoftCryptoService should fail signing with unknown wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        fun verifySign(key: GeneratedWrappedKey) {
            assertThrows<CryptoServiceBadRequestException> {
                cryptoService.sign(
                    SigningWrappedSpec(
                        tenantId = tenantId,
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = UUID.randomUUID().toString(),
                        signatureScheme = signatureScheme,
                        encodingVersion = key.encodingVersion
                    ),
                    UUID.randomUUID().toString().toByteArray(),
                    EMPTY_CONTEXT
                )
            }
        }
        verifySign(softAliasedKeys.getValue(signatureScheme))
        verifySign(softFreshKeys.getValue(signatureScheme))
    }

    @Test
    fun `SoftCryptoService should generate deterministic signatures for EdDSA, SPHINCS-256 and RSA`() {
        fun verifySign(key: GeneratedWrappedKey, signatureScheme: SignatureScheme) {
            val testData = UUID.randomUUID().toString().toByteArray()
            val signedData1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            val signedData2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            assertArrayEquals(signedData1stTime, signedData2ndTime)
            val signedZeroArray1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                EMPTY_CONTEXT
            )
            val signedZeroArray2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                EMPTY_CONTEXT
            )
            assertArrayEquals(signedZeroArray1stTime, signedZeroArray2ndTime)
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
        }
        listOf(
            schemeMetadata.schemes.first { it.codeName == EDDSA_ED25519_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == SPHINCS256_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == RSA_CODE_NAME }
        ).forEach { signatureScheme ->
            verifySign(softAliasedKeys.getValue(signatureScheme), signatureScheme)
            verifySign(softFreshKeys.getValue(signatureScheme), signatureScheme)
        }
    }

    @Test
    fun `SoftCryptoService should generate non deterministic signatures for ECDSA`() {
        fun verifySign(key: GeneratedWrappedKey, signatureScheme: SignatureScheme) {
            val testData = UUID.randomUUID().toString().toByteArray()
            val signedData1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            val signedData2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))
            val signedZeroArray1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                EMPTY_CONTEXT
            )
            val signedZeroArray2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                zeroBytes,
                EMPTY_CONTEXT
            )
            assertNotEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))
        }
        listOf(
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256K1_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        ).forEach { signatureScheme ->
            verifySign(softAliasedKeys.getValue(signatureScheme), signatureScheme)
            verifySign(softFreshKeys.getValue(signatureScheme), signatureScheme)
        }
    }

    @Test
    fun `SoftCryptoService should generate RSA key pair`() {
        val signatureScheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        assertEquals("RSA", softAliasedKeys.getValue(signatureScheme).publicKey.algorithm)
        assertEquals("RSA", softFreshKeys.getValue(signatureScheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should generate ECDSA key pair with secp256k1 curve`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("secp256k1"), (publicKey as ECKey).parameters)
        }
        val signatureScheme = schemeMetadata.findSignatureScheme(ECDSA_SECP256K1_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(signatureScheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(signatureScheme).publicKey)
    }

    @Test
    fun `SoftCryptoService should generate ECDSA key pair with secp256r1 curve`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("secp256r1"), (publicKey as ECKey).parameters)
        }
        val signatureScheme = schemeMetadata.findSignatureScheme(ECDSA_SECP256R1_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(signatureScheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(signatureScheme).publicKey)
    }

    @Test
    fun `SoftCryptoService should generate EdDSA key pair with ED25519 curve`() {
        val signatureScheme = schemeMetadata.findSignatureScheme(EDDSA_ED25519_CODE_NAME)
        assertEquals("Ed25519", softAliasedKeys.getValue(signatureScheme).publicKey.algorithm)
        assertEquals("Ed25519", softFreshKeys.getValue(signatureScheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should generate SPHINCS-256 key pair`() {
        val signatureScheme = schemeMetadata.findSignatureScheme(SPHINCS256_CODE_NAME)
        assertEquals("SPHINCS-256", softAliasedKeys.getValue(signatureScheme).publicKey.algorithm)
        assertEquals("SPHINCS-256", softFreshKeys.getValue(signatureScheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should generate SM2 key pair`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("sm2p256v1"), (publicKey as ECKey).parameters)
        }
        val signatureScheme = schemeMetadata.findSignatureScheme(SM2_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(signatureScheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(signatureScheme).publicKey)
    }

    @Test
    fun `SoftCryptoService should generate GOST3410_GOST3411 key pair`() {
        val signatureScheme = schemeMetadata.findSignatureScheme(GOST3410_GOST3411_CODE_NAME)
        assertEquals("GOST3410", softAliasedKeys.getValue(signatureScheme).publicKey.algorithm)
        assertEquals("GOST3410", softFreshKeys.getValue(signatureScheme).publicKey.algorithm)
    }

    @Test
    fun `SoftCryptoService should fail when generating key pair with unsupported signature scheme`() {
        assertThrows<CryptoServiceBadRequestException> {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = tenantId,
                    signatureScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = wrappingKeyAlias,
                    secret = null
                ),
                EMPTY_CONTEXT
            )
        }
        assertThrows<CryptoServiceBadRequestException> {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = tenantId,
                    signatureScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    alias = null,
                    masterKeyAlias = wrappingKeyAlias,
                    secret = null
                ),
                EMPTY_CONTEXT
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `SoftCryptoService should fail to use aliased key generated for another wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val anotherWrappingKey = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(anotherWrappingKey, true, EMPTY_CONTEXT)
        val testData = UUID.randomUUID().toString().toByteArray()
        val key = softAliasedKeys.getValue(signatureScheme)
        assertThrows<CryptoServiceException> {
            cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = anotherWrappingKey,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `SoftCryptoService should fail to use fresh key generated for another wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val anotherWrappingKey = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(anotherWrappingKey, true, EMPTY_CONTEXT)
        val testData = UUID.randomUUID().toString().toByteArray()
        val key = softFreshKeys.getValue(signatureScheme)
        assertThrows<CryptoServiceException> {
            cryptoService.sign(
                SigningWrappedSpec(
                    tenantId = tenantId,
                    keyMaterial = key.keyMaterial,
                    masterKeyAlias = anotherWrappingKey,
                    signatureScheme = signatureScheme,
                    encodingVersion = key.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
        }
    }

    @Test
    fun `Should generate RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        val rsaPss = SignatureSpec(
            signatureName = "RSASSA-PSS",
            params = PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
        val info = signingAliasedKeys.getValue(signatureScheme)
        assertEquals(info.publicKey.algorithm, "RSA")
        val customSignature1 = info.signingService.sign(
            tenantId,
            info.publicKey,
            rsaPss,
            testData
        )
        assertEquals(info.publicKey, customSignature1.by)
        validateSignature(info.publicKey, rsaPss, customSignature1.bytes, testData)
    }

    @Test
    fun `Should generate fresh RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        val rsaPss = SignatureSpec(
            signatureName = "RSASSA-PSS",
            params = PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
        val info = signingFreshKeys.getValue(signatureScheme)
        assertNotNull(info.publicKey)
        assertEquals(info.publicKey.algorithm, "RSA")
        val customSignature = info.signingService.sign(
            tenantId,
            info.publicKey,
            rsaPss,
            testData
        )
        assertEquals(info.publicKey, customSignature.by)
        validateSignature(info.publicKey, rsaPss, customSignature.bytes, testData)
    }


    @Test
    fun `Filtering correctly our keys`() {
        val key1 = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val key2 = signingFreshKeys.values.first().publicKey
        val ourKeys = signingFreshKeys.values.first().signingService.lookup(
            tenantId,
            listOf(publicKeyIdOf(key1), publicKeyIdOf(key2))
        ).toList()
        assertThat(ourKeys, IsCollectionWithSize.hasSize(1))
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
            listOf(publicKeyIdOf(key1), publicKeyIdOf(key2))
        ).toList()
        assertThat(ourKeys, `is`(IsEmptyCollection.empty()))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should lookup by id for aliased key in all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingAliasedKeys.getValue(signatureScheme)
        val returned = info.signingService.lookup(tenantId, listOf(publicKeyIdOf(info.publicKey)))
        assertEquals(1, returned.size)
        verifySigningKeyInfo(info.publicKey, info.alias, signatureScheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, info.alias, null, signatureScheme)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should lookup by id for fresh key in all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingFreshKeys.getValue(signatureScheme)
        val returned = info.signingService.lookup(tenantId, listOf(publicKeyIdOf(info.publicKey)))
        assertEquals(1, returned.size)
        verifySigningKeyInfo(info.publicKey, null, signatureScheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, null, info.externalId, signatureScheme)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should return empty collection when looking up for not existing ids in all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingAliasedKeys.getValue(signatureScheme)
        val returned = info.signingService.lookup(
            tenantId, listOf(publicKeyIdOf(UUID.randomUUID().toString().toByteArray()))
        )
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should lookup for key in all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingAliasedKeys.getValue(signatureScheme)
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
        verifySigningKeyInfo(info.publicKey, info.alias, signatureScheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, info.alias, null, signatureScheme)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should return empty collection when looking up for noy matching key parameters in all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingAliasedKeys.getValue(signatureScheme)
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
        signatureScheme: SignatureScheme
    ) {
        val unknownPublicKey = unknownKeyPairs.getValue(signatureScheme).public
        val info = signingFreshKeys.getValue(signatureScheme)
        val returned = info.signingService.lookup(tenantId, listOf(publicKeyIdOf(unknownPublicKey)))
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should fail signing with unknown public key for all supported schemes`(signatureScheme: SignatureScheme) {
        val testData = UUID.randomUUID().toString().toByteArray()
        val unknownPublicKey = unknownKeyPairs.getValue(signatureScheme).public
        val info = signingFreshKeys.getValue(signatureScheme)
        assertThrows<CryptoServiceException> {
            info.signingService.sign(
                tenantId,
                unknownPublicKey,
                testData
            )
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should generate aliased keys and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingAliasedKeys.getValue(signatureScheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        verifyCachedKeyRecord(info.publicKey, info.alias, null, signatureScheme)
        validatePublicKeyAlgorithm(signatureScheme, info.publicKey)
        val signatureByPublicKey = info.signingService.sign(tenantId, info.publicKey, testData)
        assertEquals(info.publicKey, signatureByPublicKey.by)
        validateSignature(info.publicKey, signatureByPublicKey.bytes, testData)
        assertThrows<CryptoServiceBadRequestException> {
            info.signingService.sign(UUID.randomUUID().toString(), info.publicKey, testData)
        }
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            val customSignatureByPublicKey = info.signingService.sign(
                tenantId,
                info.publicKey,
                signatureSpec,
                testData
            )
            assertEquals(info.publicKey, customSignatureByPublicKey.by)
            validateSignature(info.publicKey, signatureSpec, customSignatureByPublicKey.bytes, testData)
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should generate fresh keys and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingFreshKeys.getValue(signatureScheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        verifyCachedKeyRecord(info.publicKey, null, info.externalId, signatureScheme)
        validatePublicKeyAlgorithm(signatureScheme, info.publicKey)
        val signatureByPublicKey = info.signingService.sign(tenantId, info.publicKey, testData)
        assertEquals(info.publicKey, signatureByPublicKey.by)
        validateSignature(info.publicKey, signatureByPublicKey.bytes, testData)
        assertThrows<CryptoServiceBadRequestException> {
            info.signingService.sign(UUID.randomUUID().toString(), info.publicKey, testData)
        }
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            val customSignatureByPublicKey = info.signingService.sign(
                tenantId,
                info.publicKey,
                signatureSpec,
                testData
            )
            assertEquals(info.publicKey, customSignatureByPublicKey.by)
            validateSignature(info.publicKey, signatureSpec, customSignatureByPublicKey.bytes, testData)
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Should generate fresh keys without external id and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingFreshKeysWithoutExternalId.getValue(signatureScheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        verifyCachedKeyRecord(info.publicKey, null, null, signatureScheme)
        validatePublicKeyAlgorithm(signatureScheme, info.publicKey)
        val signatureByPublicKey = info.signingService.sign(tenantId, info.publicKey, testData)
        assertEquals(info.publicKey, signatureByPublicKey.by)
        validateSignature(info.publicKey, signatureByPublicKey.bytes, testData)
        assertThrows<CryptoServiceBadRequestException> {
            info.signingService.sign(UUID.randomUUID().toString(), info.publicKey, testData)
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Signing service should use first known aliased key from CompositeKey when signing for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingAliasedKeys.getValue(signatureScheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val bobPublicKey = info.publicKey
        verifyCachedKeyRecord(bobPublicKey, info.alias, null, signatureScheme)
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signature = info.signingService.sign(tenantId, aliceAndBob, testData)
        assertEquals(bobPublicKey, signature.by)
        validateSignature(signature.by, signature.bytes, testData)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    fun `Signing service should use first known fresh key from CompositeKey when signing for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val info = signingFreshKeys.getValue(signatureScheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val bobPublicKey = info.publicKey
        verifyCachedKeyRecord(bobPublicKey, null, info.externalId, signatureScheme)
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signature = info.signingService.sign(tenantId, aliceAndBob, testData)
        assertEquals(bobPublicKey, signature.by)
        validateSignature(signature.by, signature.bytes, testData)
    }
}

