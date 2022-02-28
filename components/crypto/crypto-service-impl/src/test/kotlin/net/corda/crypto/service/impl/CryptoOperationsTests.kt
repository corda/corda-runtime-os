package net.corda.crypto.service.impl

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.impl.signing.CryptoServicesTestFactory
import net.corda.test.util.createTestCase
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.cipher.suite.CipherSchemeMetadata
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
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.i2p.crypto.eddsa.EdDSAKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        private lateinit var factory: CryptoServicesTestFactory
        private lateinit var services: CryptoServicesTestFactory.CryptoServices
        private lateinit var schemeMetadata: CipherSchemeMetadata

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

        @JvmStatic
        @BeforeAll
        fun setup() {
            factory = CryptoServicesTestFactory()
            services = factory.createCryptoServices()
            schemeMetadata = factory.schemeMetadata
        }

        @JvmStatic
        fun supportedSchemes(): Array<SignatureScheme> {
            return services.cryptoService.supportedSchemes()
        }

        @JvmStatic
        fun supportedWrappingSchemes(): Array<SignatureScheme> {
            return services.cryptoService.supportedWrappingSchemes()
        }

        fun getAllCustomSignatureSpecs(scheme: SignatureScheme): List<SignatureSpec> =
            if(scheme.codeName == RSA_CODE_NAME || scheme.codeName == ECDSA_SECP256R1_CODE_NAME) {
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

        private fun newAlias(): String = UUID.randomUUID().toString()

        private fun verifyGeneratedAliasedKeyRecord(
            generatedPublicKey: PublicKey,
            alias: String?,
            signatureScheme: SignatureScheme
        ) {
            val generatedKeyData = services.getSigningKeyRecord(generatedPublicKey)
            assertNotNull(generatedKeyData)
            assertEquals(services.tenantId, generatedKeyData.tenantId)
            assertEquals(services.category, generatedKeyData.category)
            assertNull(generatedKeyData.externalId)
            assertArrayEquals(generatedPublicKey.encoded, generatedKeyData.publicKey.array())
            if(alias == null) {
                assertNull(generatedKeyData.alias)
                assertNull(generatedKeyData.hsmAlias)
            } else {
                assertEquals(alias, generatedKeyData.alias)
                assertNotNull(generatedKeyData.hsmAlias)
                assertNotEquals(alias, generatedKeyData.hsmAlias)
            }
            assertNull(generatedKeyData.privateKeyMaterial)
            assertEquals(signatureScheme.codeName, generatedKeyData.schemeCodeName)
            assertEquals(1, generatedKeyData.version)
        }

        private fun verifyFreshKeyRecord(freshKey: PublicKey, uuid: UUID?, signatureScheme: SignatureScheme) {
            val freshKeyData = services.getSigningKeyRecord(freshKey)
            assertNotNull(freshKeyData)
            assertEquals(services.tenantId, freshKeyData.tenantId)
            assertEquals(CryptoConsts.Categories.FRESH_KEYS, freshKeyData.category)
            if (uuid != null)
                assertEquals(uuid, UUID.fromString(freshKeyData.externalId))
            else
                assertNull(freshKeyData.externalId)
            assertArrayEquals(freshKey.encoded, freshKeyData.publicKey.array())
            assertNull(freshKeyData.alias)
            assertNull(freshKeyData.hsmAlias)
            assertNotNull(freshKeyData.privateKeyMaterial)
            assertEquals(signatureScheme.codeName, freshKeyData.schemeCodeName)
            assertEquals(1, freshKeyData.version)
        }

        private fun validateSignature(
            publicKey: PublicKey,
            signature: ByteArray,
            data: ByteArray
        ) {
            val badData = UUID.randomUUID().toString().toByteArray()
            val verifier = factory.verifier
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
            val verifier = factory.verifier
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
    }

    @Test
    @Timeout(10)
    fun `SoftCryptoService should require wrapping key`() {
        assertTrue(services.cryptoService.requiresWrappingKey())
    }

    @Test
    @Timeout(10)
    fun `All supported by SoftCryptoService schemes should be defined in cipher suite`() {
        assertTrue(services.cryptoService.supportedSchemes().isNotEmpty())
        services.cryptoService.supportedSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(10)
    fun `All supported by SoftCryptoService wrapping schemes should be defined in cipher suite`() {
        assertTrue(services.cryptoService.supportedWrappingSchemes().isNotEmpty())
        services.cryptoService.supportedWrappingSchemes().forEach {
            assertTrue(schemeMetadata.schemes.contains(it))
        }
    }

    @Test
    @Timeout(10)
    fun `containsKey in SoftCryptoService should return false for unknown alias`() {
        val alias = newAlias()
        assertFalse(services.cryptoService.containsKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(60)
    fun `containsKey in SoftCryptoService should return true for known alias for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alias = newAlias()
        services.cryptoService.generateKeyPair(alias, signatureScheme, EMPTY_CONTEXT)
        assertTrue(services.cryptoService.containsKey(alias))
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(60)
    @Suppress("MaxLineLength")
    fun `SoftCryptoService should fail signing using wrapped key pair with unknown wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val wrappedKeyPair = services.cryptoService.generateWrappedKeyPair(
            services.wrappingKeyAlias,
            signatureScheme,
            EMPTY_CONTEXT
        )
        assertThrows<CryptoServiceBadRequestException> {
            services.cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                UUID.randomUUID().toString().toByteArray(),
                EMPTY_CONTEXT
            )
        }
    }

    @Test
    @Timeout(60)
    fun `Should generate deterministic signatures for EdDSA, SPHINCS-256 and RSA`() {
        listOf(
            schemeMetadata.schemes.first { it.codeName == EDDSA_ED25519_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == SPHINCS256_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == RSA_CODE_NAME }
        ).forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            services.cryptoService.generateKeyPair(alias, signatureScheme, EMPTY_CONTEXT)
            val signedData1stTime = services.cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
            val signedData2ndTime = services.cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
            assertArrayEquals(signedData1stTime, signedData2ndTime)
            val signedZeroArray1stTime = services.cryptoService.sign(alias, signatureScheme, zeroBytes, EMPTY_CONTEXT)
            val signedZeroArray2ndTime = services.cryptoService.sign(alias, signatureScheme, zeroBytes, EMPTY_CONTEXT)
            assertArrayEquals(signedZeroArray1stTime, signedZeroArray2ndTime)
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
            val wrappedKeyPair = services.cryptoService.generateWrappedKeyPair(
                services.wrappingKeyAlias,
                signatureScheme,
                EMPTY_CONTEXT
            )
            val signature1 = services.cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = services.wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            val signature2 = services.cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = services.wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            assertArrayEquals(signature1, signature2)
        }
    }

    @Test
    @Timeout(10)
    fun `Should generate non deterministic signatures for ECDSA`() {
        listOf(
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256K1_CODE_NAME },
            schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        ).forEach { signatureScheme ->
            val testData = UUID.randomUUID().toString().toByteArray()
            val alias = newAlias()
            services.cryptoService.generateKeyPair(alias, signatureScheme, EMPTY_CONTEXT)
            val signedData1stTime = services.cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
            val signedData2ndTime = services.cryptoService.sign(alias, signatureScheme, testData, EMPTY_CONTEXT)
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))
            val signedZeroArray1stTime = services.cryptoService.sign(alias, signatureScheme, zeroBytes, EMPTY_CONTEXT)
            val signedZeroArray2ndTime = services.cryptoService.sign(alias, signatureScheme, zeroBytes, EMPTY_CONTEXT)
            assertNotEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
            val wrappedKeyPair = services.cryptoService.generateWrappedKeyPair(
                services.wrappingKeyAlias,
                signatureScheme,
                EMPTY_CONTEXT
            )
            val signature1 = services.cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = services.wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            val signature2 = services.cryptoService.sign(
                WrappedPrivateKey(
                    keyMaterial = wrappedKeyPair.keyMaterial,
                    masterKeyAlias = services.wrappingKeyAlias,
                    signatureScheme = signatureScheme,
                    encodingVersion = wrappedKeyPair.encodingVersion
                ),
                testData,
                EMPTY_CONTEXT
            )
            assertNotEquals(OpaqueBytes(signature1), OpaqueBytes(signature2))
        }
    }

    @Test
    @Timeout(10)
    fun `Should generate RSA key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "RSA")
        assertEquals(keyPair.public.algorithm, "RSA")
    }

    @Test
    @Timeout(10)
    fun `Should generate RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val data = UUID.randomUUID().toString().toByteArray()
        val alias = newAlias()
        val signatureScheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        val signingService = services.createSigningService(signatureScheme)
        val rsaPss = SignatureSpec(
            signatureName = "RSASSA-PSS",
            params = PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1)
        )
        val publicKey = signingService.generateKeyPair(CryptoConsts.Categories.LEDGER, alias)
        assertNotNull(publicKey)
        assertEquals(publicKey.algorithm, "RSA")
        val customSignature1 = signingService.sign(publicKey, rsaPss, data)
        assertEquals(publicKey, customSignature1.by)
        validateSignature(publicKey, rsaPss, customSignature1.bytes, data)
        val customSignature2 = signingService.sign(alias, rsaPss, data)
        assertFalse(customSignature1.bytes.contentEquals(customSignature2))
        validateSignature(publicKey, rsaPss, customSignature2, data)
    }

    @Test
    @Timeout(10)
    fun `Should generate wrapped RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val data = UUID.randomUUID().toString().toByteArray()
        val signatureScheme = schemeMetadata.findSignatureScheme(RSA_CODE_NAME)
        val signingService = services.createSigningService(signatureScheme)
        val rsaPss = SignatureSpec(
            signatureName = "RSASSA-PSS",
            params = PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1)
        )
        val publicKey = signingService.freshKey()
        assertNotNull(publicKey)
        assertEquals(publicKey.algorithm, "RSA")
        val customSignature = signingService.sign(publicKey, rsaPss, data)
        assertEquals(publicKey, customSignature.by)
        validateSignature(publicKey, rsaPss, customSignature.bytes, data)
    }

    @Test
    @Timeout(5)
    fun `Should generate ECDSA key pair with secp256k1 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(ECDSA_SECP256K1_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "EC")
        assertEquals((keyPair.private as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
        assertEquals(keyPair.public.algorithm, "EC")
        assertEquals((keyPair.public as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256k1"))
    }

    @Test
    @Timeout(5)
    fun `Should generate ECDSA key pair with secp256r1 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(ECDSA_SECP256R1_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "EC")
        assertEquals((keyPair.private as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
        assertEquals(keyPair.public.algorithm, "EC")
        assertEquals((keyPair.public as ECKey).parameters, ECNamedCurveTable.getParameterSpec("secp256r1"))
    }

    @Test
    @Timeout(5)
    fun `Should generate EdDSA key pair with ED25519 curve`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(EDDSA_ED25519_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "EdDSA")
        assertEquals((keyPair.private as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
        assertEquals(keyPair.public.algorithm, "EdDSA")
        assertEquals((keyPair.public as EdDSAKey).params, EdDSANamedCurveTable.getByName("ED25519"))
    }

    @Test
    @Timeout(5)
    fun `Should generate SPHINCS-256 key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(SPHINCS256_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "SPHINCS-256")
        assertEquals(keyPair.public.algorithm, "SPHINCS-256")
    }

    @Test
    @Timeout(5)
    fun `Should generate SM2 key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(SM2_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "EC")
        assertEquals((keyPair.private as ECKey).parameters, ECNamedCurveTable.getParameterSpec("sm2p256v1"))
        assertEquals(keyPair.public.algorithm, "EC")
        assertEquals((keyPair.public as ECKey).parameters, ECNamedCurveTable.getParameterSpec("sm2p256v1"))
    }

    @Test
    @Timeout(5)
    fun `Should generate GOST3410_GOST3411 key pair`() {
        val alias = newAlias()
        val scheme = schemeMetadata.findSignatureScheme(GOST3410_GOST3411_CODE_NAME)
        services.cryptoService.generateKeyPair(alias, scheme, EMPTY_CONTEXT)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        assertEquals(keyPair.private.algorithm, "GOST3410")
        assertEquals(keyPair.public.algorithm, "GOST3410")
    }

    @Test
    @Timeout(30)
    fun `Should fail when generating key pair with unsupported signature scheme`() {
        val alias = newAlias()
        assertThrows<CryptoServiceBadRequestException> {
            services.cryptoService.generateKeyPair(alias, UNSUPPORTED_SIGNATURE_SCHEME, EMPTY_CONTEXT)
        }
    }
    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should find generated public key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = services.createSigningService(signatureScheme)
        val alias = newAlias()
        val generated = signingService.generateKeyPair(services.category, alias)
        val returned = signingService.findPublicKey(alias)
        assertEquals(generated, returned)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should not find public key when key pair hasn't been generated yet for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = services.createSigningService(signatureScheme)
        val alias = newAlias()
        val publicKey = signingService.findPublicKey(alias)
        assertNull(publicKey)
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should fail signing with unknown alias for all supported schemes`(signatureScheme: SignatureScheme) {
        val alias = newAlias()
        val data = UUID.randomUUID().toString().toByteArray()
        val signingService = services.createSigningService(signatureScheme)
        assertThrows<CryptoServiceException> {
            signingService.sign(alias, data)
        }
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            assertThrows<CryptoServiceException> {
                signingService.sign(alias, signatureSpec, data)
            }
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `Should generate keys and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = services.createSigningService(signatureScheme)
        val otherMemberSigningService = factory.createCryptoServices(
            tenantId = UUID.randomUUID().toString()
        ) .createSigningService(signatureScheme)
        for (i in 1..3) {
            val alias = newAlias()
            val data = UUID.randomUUID().toString().toByteArray()
            val publicKey = signingService.generateKeyPair(services.category, alias)
            verifyGeneratedAliasedKeyRecord(publicKey, alias, signatureScheme)
            validatePublicKeyAlgorithm(signatureScheme, publicKey)
            val signatureByPublicKey = signingService.sign(publicKey, data)
            assertEquals(publicKey, signatureByPublicKey.by)
            validateSignature(publicKey, signatureByPublicKey.bytes, data)
            val signatureByAlias = signingService.sign(alias, data)
            validateSignature(publicKey, signatureByAlias, data)
            assertThrows<CryptoServiceBadRequestException> {
                otherMemberSigningService.sign(publicKey, data)
            }
            assertThrows<CryptoServiceBadRequestException> {
                otherMemberSigningService.sign(alias, data)
            }
            getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
                val customSignatureByPublicKey = signingService.sign(publicKey, signatureSpec, data)
                assertEquals(publicKey, customSignatureByPublicKey.by)
                validateSignature(publicKey, signatureSpec, customSignatureByPublicKey.bytes, data)
                val customSignatureByAlias = signingService.sign(alias, signatureSpec, data)
                validateSignature(publicKey, signatureSpec, customSignatureByAlias, data)
                assertThrows<CryptoServiceBadRequestException> {
                    otherMemberSigningService.sign(publicKey, data)
                }
                assertThrows<CryptoServiceBadRequestException> {
                    otherMemberSigningService.sign(alias, data)
                }
            }.runAndValidate()
        }
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Signing service should use first known key from CompositeKey when signing using public key overload for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val signingService = services.createSigningService(signatureScheme)
        val alias = newAlias()
        val data = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey>()
        whenever(alicePublicKey.encoded).thenReturn(ByteArray(0))
        val bobPublicKey = signingService.generateKeyPair(services.category, alias)
        verifyGeneratedAliasedKeyRecord(bobPublicKey, alias, signatureScheme)
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signature = signingService.sign(aliceAndBob, data)
        assertEquals(bobPublicKey, signature.by)
        validateSignature(signature.by, signature.bytes, data)
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            val customSignatureByPublicKey = signingService.sign(bobPublicKey, signatureSpec, data)
            assertEquals(bobPublicKey, customSignatureByPublicKey.by)
            validateSignature(bobPublicKey, signatureSpec, customSignatureByPublicKey.bytes, data)
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedSchemes")
    @Timeout(30)
    fun `SoftCryptoService should fail to use key generated for another member for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val alias = newAlias()
        val data = UUID.randomUUID().toString().toByteArray()
        val publicKey = services.cryptoService.generateKeyPair(alias, signatureScheme, EMPTY_CONTEXT)
        assertNotNull(publicKey)
        validatePublicKeyAlgorithm(signatureScheme, publicKey)
        val keyPair = services.getKeyPair(alias)
        assertNotNull(keyPair)
        val signature = services.cryptoService.sign(alias, signatureScheme, data, EMPTY_CONTEXT)
        validateSignature(publicKey, signature, data)
        val otherMemberCryptoService = factory.createCryptoServices(
            tenantId = UUID.randomUUID().toString()
        ) .cryptoService
        assertThrows<CryptoServiceBadRequestException> {
            otherMemberCryptoService.sign(alias, signatureScheme, data, EMPTY_CONTEXT)
        }
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            assertThrows<CryptoServiceBadRequestException> {
                otherMemberCryptoService.sign(
                    alias,
                    signatureScheme.copy(signatureSpec = signatureSpec),
                    data,
                    EMPTY_CONTEXT
                )
            }
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should create fresh key without external id and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = services.createSigningService(signatureScheme)
        for (i in 1..3) {
            val data = UUID.randomUUID().toString().toByteArray()
            val freshKey = freshKeyService.freshKey()
            verifyFreshKeyRecord(freshKey, null, signatureScheme)
            val signature = freshKeyService.sign(freshKey, data)
            assertEquals(freshKey, signature.by)
            validateSignature(freshKey, signature.bytes, data)
            getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
                val customSignature = freshKeyService.sign(freshKey, signatureSpec, data)
                assertEquals(freshKey, customSignature.by)
                validateSignature(freshKey, signatureSpec, customSignature.bytes, data)
            }.runAndValidate()
        }
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should create fresh key with external id and then sign and verify for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = services.createSigningService(signatureScheme)
        for (i in 1..3) {
            val uuid = UUID.randomUUID()
            val data = UUID.randomUUID().toString().toByteArray()
            val freshKey = freshKeyService.freshKey(uuid)
            verifyFreshKeyRecord(freshKey, uuid, signatureScheme)
            val signature = freshKeyService.sign(freshKey, data)
            assertEquals(freshKey, signature.by)
            validateSignature(freshKey, signature.bytes, data)
            getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
                val customSignature = freshKeyService.sign(freshKey, signatureSpec, data)
                assertEquals(freshKey, customSignature.by)
                validateSignature(freshKey, signatureSpec, customSignature.bytes, data)
            }.runAndValidate()
        }
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should still be able to use fresh key without external id generated and with previous master wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val effectiveWrappingKeyAlias1 = UUID.randomUUID().toString()
        val freshKeyService1 = services.createSigningService(
            signatureScheme = signatureScheme,
            effectiveWrappingKeyAlias = effectiveWrappingKeyAlias1
        )
        services.cryptoService.createWrappingKey(effectiveWrappingKeyAlias1, true)
        val effectiveWrappingKeyAlias2 = UUID.randomUUID().toString()
        val freshKeyService2 = services.createSigningService(
            signatureScheme = signatureScheme,
            effectiveWrappingKeyAlias = effectiveWrappingKeyAlias2
        )
        services.cryptoService.createWrappingKey(effectiveWrappingKeyAlias2, true)
        val freshKey1 = freshKeyService1.freshKey()
        verifyFreshKeyRecord(freshKey1, null, signatureScheme)
        val freshKey2 = freshKeyService2.freshKey()
        verifyFreshKeyRecord(freshKey2, null, signatureScheme)
        // it has to be freshKeyService2 in both cases to emulate wrapping key rotation
        val signature1 = freshKeyService2.sign(freshKey1, data)
        assertEquals(freshKey1, signature1.by)
        validateSignature(freshKey1, signature1.bytes, data)
        val signature2 = freshKeyService2.sign(freshKey2, data)
        assertEquals(freshKey2, signature2.by)
        validateSignature(freshKey2, signature2.bytes, data)
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            val customSignature1 = freshKeyService2.sign(freshKey1, signatureSpec, data)
            assertEquals(freshKey1, customSignature1.by)
            validateSignature(freshKey1, signatureSpec, customSignature1.bytes, data)
            val customSignature2 = freshKeyService2.sign(freshKey2, signatureSpec, data)
            assertEquals(freshKey2, customSignature2.by)
            validateSignature(freshKey2, signatureSpec, customSignature2.bytes, data)
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Should still be able to use fresh key with external id generated and with previous master wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val data = UUID.randomUUID().toString().toByteArray()
        val effectiveWrappingKeyAlias1 = UUID.randomUUID().toString()
        val freshKeyService1 = services.createSigningService(
            signatureScheme = signatureScheme,
            effectiveWrappingKeyAlias = effectiveWrappingKeyAlias1
        )
        services.cryptoService.createWrappingKey(effectiveWrappingKeyAlias1, true)
        val effectiveWrappingKeyAlias2 = UUID.randomUUID().toString()
        val freshKeyService2 = services.createSigningService(
            signatureScheme = signatureScheme,
            effectiveWrappingKeyAlias = effectiveWrappingKeyAlias2
        )
        services.cryptoService.createWrappingKey(effectiveWrappingKeyAlias2, true)
        val freshKey1 = freshKeyService1.freshKey(uuid1)
        verifyFreshKeyRecord(freshKey1, uuid1, signatureScheme)
        val freshKey2 = freshKeyService2.freshKey(uuid2)
        verifyFreshKeyRecord(freshKey2, uuid2, signatureScheme)
        // it has to be freshKeyService2 in both cases to emulate wrapping key rotation
        val signature1 = freshKeyService2.sign(freshKey1, data)
        assertEquals(freshKey1, signature1.by)
        validateSignature(freshKey1, signature1.bytes, data)
        val signature2 = freshKeyService2.sign(freshKey2, data)
        assertEquals(freshKey2, signature2.by)
        validateSignature(freshKey2, signature2.bytes, data)
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            val customSignature1 = freshKeyService2.sign(freshKey1, signatureSpec, data)
            assertEquals(freshKey1, customSignature1.by)
            validateSignature(freshKey1, signatureSpec, customSignature1.bytes, data)
            val customSignature2 = freshKeyService2.sign(freshKey2, signatureSpec, data)
            assertEquals(freshKey2, customSignature2.by)
            validateSignature(freshKey2, signatureSpec, customSignature2.bytes, data)
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    @Suppress("MaxLineLength")
    fun `Fresh key service should use first known key from CompositeKey when signing using public key overload for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = services.createSigningService(signatureScheme)
        val alicePublicKey = mock<PublicKey>()
        whenever(alicePublicKey.encoded).thenReturn(ByteArray(0))
        val bobPublicKey = freshKeyService.freshKey()
        verifyFreshKeyRecord(bobPublicKey, null, signatureScheme)
        val data = UUID.randomUUID().toString().toByteArray()
        val aliceAndBob = CompositeKey.Builder()
            .addKey(alicePublicKey, 2)
            .addKey(bobPublicKey, 1)
            .build(threshold = 2)
        val signature = freshKeyService.sign(aliceAndBob, data)
        assertEquals(bobPublicKey, signature.by)
        validateSignature(signature.by, signature.bytes, data)
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            val customSignatureByPublicKey = freshKeyService.sign(bobPublicKey, signatureSpec, data)
            assertEquals(bobPublicKey, customSignatureByPublicKey.by)
            validateSignature(bobPublicKey, signatureSpec, customSignatureByPublicKey.bytes, data)
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail to use fresh key to sign generated for different member for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val data = UUID.randomUUID().toString().toByteArray()
        val freshKeyService = services.createSigningService(signatureScheme)
        val freshKey = freshKeyService.freshKey()
        verifyFreshKeyRecord(freshKey, null, signatureScheme)
        val signature = freshKeyService.sign(freshKey, data)
        assertEquals(freshKey, signature.by)
        validateSignature(freshKey, signature.bytes, data)
        val otherMemberFreshKeyService = factory.createCryptoServices(
            tenantId = UUID.randomUUID().toString()
        ) .createSigningService(signatureScheme)
        assertThrows<CryptoServiceBadRequestException> {
            otherMemberFreshKeyService.sign(freshKey, data)
        }
        getAllCustomSignatureSpecs(signatureScheme).createTestCase { signatureSpec ->
            assertThrows<CryptoServiceBadRequestException> {
                otherMemberFreshKeyService.sign(freshKey, signatureSpec, data)
            }
        }.runAndValidate()
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail generating fresh key with unknown wrapping key for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = services.createSigningService(
            signatureScheme = signatureScheme,
            effectiveWrappingKeyAlias = UUID.randomUUID().toString()
        )
        assertThrows<CryptoServiceBadRequestException> {
            freshKeyService.freshKey()
        }
        assertThrows<CryptoServiceBadRequestException> {
            freshKeyService.freshKey(UUID.randomUUID())
        }
    }

    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail filtering my keys as it's not implemented yet for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = services.createSigningService(signatureScheme)
        val freshKey1 = freshKeyService.freshKey()
        val freshKey2 = freshKeyService.freshKey(UUID.randomUUID())
        assertThrows<NotImplementedError> {
            freshKeyService.filterMyKeys(mutableListOf(freshKey1, freshKey2))
        }
    }

    /*
    @ParameterizedTest
    @MethodSource("supportedWrappingSchemes")
    @Timeout(30)
    fun `Should fail filtering my keys as it's not implemented yet for all supported schemes`(
        signatureScheme: SignatureScheme
    ) {
        val freshKeyService = createFreshKeyService(signatureScheme, wrappingKeyAlias1)
        val freshKey1 = freshKeyService.freshKey()
        val freshKey2 = freshKeyService.freshKey(UUID.randomUUID())
        val ourKeys = freshKeyService.filterMyKeys(mutableListOf(freshKey1, freshKey2)).toList()
        assertThat(ourKeys, IsCollectionWithSize.hasSize(2))
        assertThat(ourKeys, hasItem(freshKey1))
        assertThat(ourKeys, hasItem(freshKey2))
    }

     @Test
    @Timeout(30)
    fun `Keys are correctly filtered - none keys belong to us`() {
        val freshKey1 = mockSigningService.generateKeyPair(UUID.randomUUID().toString(), defaultScheme)
        val freshKey2 = keyManagementBackend1.freshKey()
        val ourKeys = keyManagementBackend1.filterMyKeys(mutableListOf(freshKey1, freshKey2)).toList()
        MatcherAssert.assertThat(ourKeys, IsCollectionWithSize.hasSize(1))
        MatcherAssert.assertThat(ourKeys, hasItem(freshKey2))
    }

    @Test
    @Timeout(30)
    fun `Keys are correctly filtered - some of the keys belong to us`() {
        val freshKey1 = mockSigningService.generateKeyPair(UUID.randomUUID().toString(), defaultScheme)
        val freshKey2 = mockSigningService.generateKeyPair(UUID.randomUUID().toString(), defaultScheme)

        val ourKeys = keyManagementBackend1.filterMyKeys(mutableListOf(freshKey1, freshKey2)).toList()
        MatcherAssert.assertThat(ourKeys, `is`(IsEmptyCollection.empty()))
    }
*/
}

