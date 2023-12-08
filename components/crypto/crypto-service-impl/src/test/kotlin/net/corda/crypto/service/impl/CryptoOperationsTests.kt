package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.SignatureVerificationService
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.hes.HybridEncryptionParams
import net.corda.crypto.hes.impl.EphemeralKeyPairEncryptorImpl
import net.corda.crypto.hes.impl.StableKeyPairDecryptorImpl
import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.service.impl.infra.TestCryptoOpsClient
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.softhsm.impl.WRAPPING_KEY_ENCODING_VERSION
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.createTestCase
import net.corda.test.util.eventually
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jcajce.provider.util.DigestFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        class SigningAliasedKeyInfo(
            val alias: String,
            val publicKey: PublicKey,
        )

        class SigningFreshKeyInfo(
            val externalId: String?,
            val publicKey: PublicKey,
        )

        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var verifier: SignatureVerificationService
        private lateinit var tenantId: String
        private lateinit var category: String
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
            CryptoConsts.Categories.all.forEach {
                factory.tenantInfoService.populate(tenantId, it, factory.cryptoService)
            }
            signingAliasedKeys = factory.cryptoService.supportedSchemes.keys.associateWith {
                val alias = UUID.randomUUID().toString()
                SigningAliasedKeyInfo(
                    alias = alias,
                    publicKey = factory.cryptoService.generateKeyPair(
                        tenantId = tenantId,
                        category = LEDGER,
                        alias = alias,
                        scheme = it
                    ).publicKey
                )
            }
            signingFreshKeys = factory.cryptoService.supportedSchemes.keys.associateWith {
                val externalId = UUID.randomUUID().toString()
                SigningFreshKeyInfo(
                    externalId = externalId,
                    publicKey = factory.cryptoService.generateKeyPair(
                        tenantId = tenantId,
                        category = CryptoConsts.Categories.CI,
                        alias = null,
                        externalId = externalId,
                        scheme = it,
                        context = emptyMap()
                    ).publicKey
                )
            }
            signingFreshKeysWithoutExternalId = factory.cryptoService.supportedSchemes.keys.associateWith {
                SigningFreshKeyInfo(
                    externalId = null,
                    publicKey = factory.cryptoService.generateKeyPair(
                        tenantId = tenantId,
                        category = CryptoConsts.Categories.CI,
                        alias = null,
                        externalId = null,
                        scheme = it,
                        context = emptyMap()
                    ).publicKey
                )
            }
            unknownKeyPairs = factory.cryptoService.supportedSchemes.keys.associateWith {
                generateKeyPair(schemeMetadata, it.codeName)
            }
        }

        @JvmStatic
        fun derivingSchemes(): List<KeyScheme> =
            factory.cryptoService.supportedSchemes.keys.filter {
                it.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION)
            }

        @JvmStatic
        fun signingSchemes(): List<Arguments> {
            val list = mutableListOf<Arguments>()
            factory.cryptoService.supportedSchemes.forEach { entry ->
                entry.value.forEach { spec ->
                    list.add(Arguments.of(entry.key, spec))
                }
            }
            return list
        }

        @JvmStatic
        fun keySchemes(): Collection<KeyScheme> =
            factory.cryptoService.supportedSchemes.keys

        private fun getInferableDigestNames(scheme: KeyScheme): List<DigestAlgorithmName> =
            schemeMetadata.inferableDigestNames(scheme)

        private fun getAllStandardSignatureSpecs(scheme: KeyScheme): List<SignatureSpec> =
            factory.cryptoService.supportedSchemes[scheme] ?: emptyList()

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
            val generatedKeyData = factory.signingRepository.findKey(publicKey)
            assertNotNull(generatedKeyData)
//            assertEquals(tenantId, generatedKeyData.tenantId)
            if (generatedKeyData.alias == null) {
                assertEquals(CryptoConsts.Categories.CI, generatedKeyData.category)
            } else {
                assertEquals(category, generatedKeyData.category)
            }
            assertEquals(uuid, generatedKeyData.externalId)
            assertEquals(publicKey, generatedKeyData.publicKey)
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
            assertThat(key.wrappingKeyAlias).isNotBlank
            assertEquals(1, key.encodingVersion)
            assertEquals(publicKey, key.publicKey)
        }

        private fun signAndValidateSignatureByInferringSignatureSpec(
            publicKey: PublicKey
        ) {
            val scheme = schemeMetadata.findKeyScheme(publicKey)
            getInferableDigestNames(scheme).createTestCase { digest ->
                val badData = UUID.randomUUID().toString().toByteArray()
                val data = UUID.randomUUID().toString().toByteArray()
                val spec = schemeMetadata.inferSignatureSpec(publicKey, digest)
                assertNotNull(spec)
                val signature = factory.cryptoService.sign(tenantId, publicKey, spec, data)
                assertEquals(publicKey, signature.by)
                assertTrue(
                    verifier.isValid(data, signature.bytes, publicKey, digest)
                )
                verifier.verify(data, signature.bytes, publicKey, digest)
                assertFalse(
                    verifier.isValid(badData, signature.bytes, publicKey, digest)
                )
                assertThrows<CryptoSignatureException> {
                    verifier.verify(badData, signature.bytes, publicKey, digest)
                }
                assertThrows<IllegalArgumentException> {
                    verifier.verify(ByteArray(0), signature.bytes, publicKey, digest)
                }
                assertThrows<IllegalArgumentException> {
                    verifier.verify(data, ByteArray(0), publicKey, digest)
                }
            }
        }

        private fun signAndValidateSignatureUsingExplicitSignatureSpec(
            publicKey: PublicKey,
        ) {
            val scheme = schemeMetadata.findKeyScheme(publicKey)
            (getAllStandardSignatureSpecs(scheme) + getAllCustomSignatureSpecs(scheme)).createTestCase { spec ->
                val data = UUID.randomUUID().toString().toByteArray()
                val signature = factory.cryptoService.sign(tenantId, publicKey, spec, data)
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
                verifier.isValid(data, signature, publicKey, signatureSpec),
                "Should validate with ${signatureSpec.signatureName}"
            )
            verifier.verify(data, signature, publicKey, signatureSpec)
            assertFalse(
                verifier.isValid(badData, signature, publicKey, signatureSpec)
            )
            assertThrows<CryptoSignatureException> {
                verifier.verify(badData, signature, publicKey, signatureSpec)
            }
            assertThrows<IllegalArgumentException> {
                verifier.verify(ByteArray(0), signature, publicKey, signatureSpec)
            }
            assertThrows<IllegalArgumentException> {
                verifier.verify(data, ByteArray(0), publicKey, signatureSpec)
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
    fun `Should generate RSA key pair and be able sign and verify using RSASSA-PSS signature`() {
        val testData = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        val rsaPss = SignatureSpecs.RSASSA_PSS_SHA256
        val info = signingAliasedKeys.getValue(scheme)
        assertEquals(info.publicKey.algorithm, "RSA")
        val customSignature1 = factory.cryptoService.sign(
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
        val rsaPss = SignatureSpecs.RSASSA_PSS_SHA256
        val info = signingFreshKeys.getValue(scheme)
        assertNotNull(info.publicKey)
        assertEquals(info.publicKey.algorithm, "RSA")
        val customSignature = factory.cryptoService.sign(
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
        val ourKeys = factory.cryptoService.lookupSigningKeysByPublicKeyShortHash(
            tenantId,
            listOf(ShortHash.of(key1.publicKeyId()), ShortHash.of(key2.publicKeyId()))
        ).toList()
        assertThat(ourKeys).hasSize(1)
        assertTrue(ourKeys.any { it.publicKey == key2 })
    }

    @Test
    fun `Filter our keys returns empty collection as none of the keys belong to us`() {
        val key1 = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val key2 = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val ourKeys = factory.cryptoService.lookupSigningKeysByPublicKeyShortHash(
            tenantId,
            listOf(ShortHash.of(key1.publicKeyId()), ShortHash.of(key2.publicKeyId()))
        ).toList()
        assertThat(ourKeys).isEmpty()
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("keySchemes")
    fun `Should lookup by id for aliased key in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned =
            factory.cryptoService.lookupSigningKeysByPublicKeyShortHash(
                tenantId,
                listOf(ShortHash.of(info.publicKey.publicKeyId()))
            )
        assertEquals(1, returned.size) // fails
        verifySigningKeyInfo(info.publicKey, info.alias, scheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
    }

    @ParameterizedTest
    @MethodSource("keySchemes")
    fun `Should lookup by short has for fresh key in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeys.getValue(scheme)
        val returned =
            factory.cryptoService.lookupSigningKeysByPublicKeyShortHash(
                tenantId,
                listOf(ShortHash.of(info.publicKey.publicKeyId()))
            )
        assertEquals(1, returned.size)
        verifySigningKeyInfo(info.publicKey, null, scheme, returned.first())
        verifyCachedKeyRecord(info.publicKey, null, info.externalId, scheme)
    }

    fun `Should return empty collection when looking up for not existing ids in all supported schemes`() {
        val returned = factory.cryptoService.lookupSigningKeysByPublicKeyShortHash(
            tenantId,
            listOf(ShortHash.of(publicKeyIdFromBytes(UUID.randomUUID().toString().toByteArray())))
        )
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("keySchemes")
    fun `Should lookup for key in all supported schemes`(
        scheme: KeyScheme,
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned = factory.cryptoService.querySigningKeys(
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
    @MethodSource("keySchemes")
    fun `Should return empty collection when looking up for noy matching key parameters in all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val returned = factory.cryptoService.querySigningKeys(
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
    @MethodSource("keySchemes")
    fun `Should not find public key when key pair hasn't been generated yet for all supported schemes`(
        scheme: KeyScheme
    ) {
        val unknownPublicKey = unknownKeyPairs.getValue(scheme).public
        val returned =
            factory.cryptoService.lookupSigningKeysByPublicKeyShortHash(
                tenantId,
                listOf(ShortHash.of(unknownPublicKey.publicKeyId()))
            )
        assertEquals(0, returned.size)
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `Should throw IllegalArgumentException signing with unknown public key for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        val unknownPublicKey = unknownKeyPairs.getValue(scheme).public
        assertThrows<IllegalArgumentException> {
            factory.cryptoService.sign(
                tenantId = tenantId,
                publicKey = unknownPublicKey,
                signatureSpec = spec,
                data = UUID.randomUUID().toString().toByteArray()
            )
        }
    }

    @Disabled("Not entirely certain this test is valid if we're getting rid of tenantId")
    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `Should throw IllegalArgumentException to sign for unknown tenant for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        assertThrows<IllegalArgumentException> {
            factory.cryptoService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = info.publicKey,
                signatureSpec = spec,
                data = UUID.randomUUID().toString().toByteArray()
            )
        }
    }

    @ParameterizedTest
    @MethodSource("keySchemes")
    fun `Should generate aliased keys and then sign and verify for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, info.alias, null, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        signAndValidateSignatureByInferringSignatureSpec(info.publicKey)
        signAndValidateSignatureUsingExplicitSignatureSpec(info.publicKey)
    }

    @ParameterizedTest
    @MethodSource("keySchemes")
    fun `Should generate fresh keys and then sign and verify for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeys.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, null, info.externalId, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        signAndValidateSignatureByInferringSignatureSpec(info.publicKey)
        signAndValidateSignatureUsingExplicitSignatureSpec(info.publicKey)
    }

    @ParameterizedTest
    @MethodSource("keySchemes")
    fun `Should generate fresh keys without external id and then sign and verify for all supported schemes`(
        scheme: KeyScheme
    ) {
        val info = signingFreshKeysWithoutExternalId.getValue(scheme)
        verifyCachedKeyRecord(info.publicKey, null, null, scheme)
        validatePublicKeyAlgorithm(scheme, info.publicKey)
        signAndValidateSignatureByInferringSignatureSpec(info.publicKey)
        signAndValidateSignatureUsingExplicitSignatureSpec(info.publicKey)
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `Signing service should use first known aliased key from CompositeKey when signing for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        val info = signingAliasedKeys.getValue(scheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val bobPublicKey = info.publicKey
        verifyCachedKeyRecord(bobPublicKey, info.alias, null, scheme)
        val aliceAndBob = CompositeKeyProviderImpl().create(
            listOf(
                CompositeKeyNodeAndWeight(alicePublicKey, 2),
                CompositeKeyNodeAndWeight(bobPublicKey, 1)
            ), threshold = 2
        )
        val signature = factory.cryptoService.sign(tenantId, aliceAndBob, spec, testData)
        assertEquals(bobPublicKey, signature.by)
        validateSignatureUsingExplicitSignatureSpec(signature.by, spec, signature.bytes, testData)
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `Signing service should use first known fresh key from CompositeKey when signing for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        val info = signingFreshKeys.getValue(scheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val alicePublicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val bobPublicKey = info.publicKey
        verifyCachedKeyRecord(bobPublicKey, null, info.externalId, scheme)
        val aliceAndBob = CompositeKeyProviderImpl().create(
            listOf(CompositeKeyNodeAndWeight(alicePublicKey, 2), CompositeKeyNodeAndWeight(bobPublicKey, 1)),
            threshold = 2
        )
        val signature = factory.cryptoService.sign(tenantId, aliceAndBob, spec, testData)
        assertEquals(bobPublicKey, signature.by)
        validateSignatureUsingExplicitSignatureSpec(signature.by, spec, signature.bytes, testData)
    }

    @ParameterizedTest
    @MethodSource("derivingSchemes")
    fun `Should generate deriving key pair and derive usable shared secret`(
        keyScheme: KeyScheme
    ) {
        val stableKeyPair = signingAliasedKeys.getValue(keyScheme)
        val coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        val cryptoOpsClient = TestCryptoOpsClient(
            coordinatorFactory,
            mock {
                on { deriveSharedSecret(any(), any(), any(), any()) } doAnswer {
                    factory.cryptoService.deriveSharedSecret(
                        it.getArgument(0),
                        it.getArgument(1),
                        it.getArgument(2),
                        it.getArgument(3)
                    )
                }
            }
        ).also { it.start() }
        val ephemeralEncryptor = EphemeralKeyPairEncryptorImpl(schemeMetadata)
        val stableDecryptor = StableKeyPairDecryptorImpl(
            coordinatorFactory,
            schemeMetadata,
            cryptoOpsClient
        ).also {
            it.start()
        }
        eventually {
            assertEquals(LifecycleStatus.UP, stableDecryptor.lifecycleCoordinator.status)
        }
        val plainText = "Hello MGM!".toByteArray()
        val cipherText = ephemeralEncryptor.encrypt(
            otherPublicKey = stableKeyPair.publicKey,
            plainText = plainText
        ) { _, _ ->
            HybridEncryptionParams(ByteArray(DigestFactory.getDigest("SHA-256").digestSize).apply {
                schemeMetadata.secureRandom.nextBytes(this)
            }, null)
        }
        val decryptedPlainTex = stableDecryptor.decrypt(
            tenantId = tenantId,
            salt = cipherText.params.salt,
            publicKey = stableKeyPair.publicKey,
            otherPublicKey = cipherText.publicKey,
            cipherText = cipherText.cipherText,
            aad = null
        )
        assertArrayEquals(plainText, decryptedPlainTex)
    }

    @Test
    fun `Should fail high level unwrap if master key alias is empty`() {
        val rsaScheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        assertThrows<java.lang.IllegalArgumentException> {
            factory.cryptoService.createWrappingKey("", true, mapOf())
        }
        assertThrows<java.lang.IllegalStateException> {
            factory.cryptoService.generateKeyPair(tenantId, LEDGER, "key1", null, rsaScheme, mapOf("parentKeyAlias" to ""))
        }
    }

    @Test
    fun `high level generateKeyPair should throw IllegalStateException when wrapping key is not found`() {
        val alias = UUID.randomUUID().toString()
        val rsaScheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        val e= assertThrows<IllegalStateException> {
            factory.cryptoService.generateKeyPair(tenantId, LEDGER, "key1", null, rsaScheme, mapOf("parentKeyAlias" to alias))
        }
        assertThat(e.message).contains("Wrapping key with alias $alias not found")
    }


    private fun testWithBadWrappingKeyInfo(info: (TestServicesFactory) -> WrappingKeyInfo): java.lang.IllegalArgumentException {
        val myFactory = TestServicesFactory()
        val rsaScheme = myFactory.schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        myFactory.wrappingRepository.saveKey(info(myFactory))
        return assertThrows<java.lang.IllegalArgumentException> {
            myFactory.cryptoService.generateKeyPair(
                tenantId,
                LEDGER,
                "key1",
                null,
                rsaScheme,
                mapOf("parentKeyAlias" to "key1")
            )
        }
    }

    @Test
    fun `generateKeyPair should throw IllegalArgumentException when key algorithm does not match master key`() {
        val e= testWithBadWrappingKeyInfo {
            WrappingKeyInfo(
                WRAPPING_KEY_ENCODING_VERSION,
                it.rootWrappingKey.algorithm + "!",
                it.rootWrappingKey.wrap(factory.secondLevelWrappingKey),
                1,
                "root",
                "key1"
            )
        }
        assertThat(e.message).contains("Expected algorithm")
    }

    @Test
    fun `generateKeyPair should throw IllegalArgumentException when encoding version is not recognised`() {
        val e= testWithBadWrappingKeyInfo {
            WrappingKeyInfo(
            WRAPPING_KEY_ENCODING_VERSION + 1,
            it.secondLevelWrappingKey.algorithm,
            it.rootWrappingKey.wrap(it.secondLevelWrappingKey),
            1, "root", "key1"
            )
        }
        assertThat(e.message).contains("Unknown wrapping key encoding. Expected to be 1")
    }

}

