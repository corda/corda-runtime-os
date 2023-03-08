package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.deriveSupportedSchemes
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.crypto.softhsm.impl.infra.makeWrappingKeyCache
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SM2_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val schemeMetadata = CipherSchemeMetadataImpl()

/* Tests that need wrapping keys */
class SoftCryptoServiceOperationsTests {
    companion object {
        private val coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        private val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        private val knownWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        private val knownWrappingKeyMaterial = rootWrappingKey.wrap(knownWrappingKey)
        private val knownWrappingKeyAlias = UUID.randomUUID().toString()
        private val wrappingKeyStore = TestWrappingKeyStore(
            coordinatorFactory,
            ConcurrentHashMap(
                listOf(
                    knownWrappingKeyAlias to WrappingKeyInfo(
                        WRAPPING_KEY_ENCODING_VERSION,
                        knownWrappingKey.algorithm,
                        knownWrappingKeyMaterial
                    )
                ).toMap()
            )
        )
        private val wrappingKeyCache = makeWrappingKeyCache()
        private val cryptoService =
            SoftCryptoService(wrappingKeyStore, schemeMetadata, rootWrappingKey, wrappingKeyCache = wrappingKeyCache)
        private val tenantId = UUID.randomUUID().toString()
        private val category = CryptoConsts.Categories.LEDGER
        private val defaultContext = mapOf(CRYPTO_TENANT_ID to tenantId, CRYPTO_CATEGORY to category)
        private val softAliasedKeys = cryptoService.supportedSchemes.keys.associateWith {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = it,
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = knownWrappingKeyAlias
                ),
                defaultContext
            )
        }
        private val softFreshKeys = cryptoService.supportedSchemes.keys.associateWith {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = it,
                    alias = null,
                    masterKeyAlias = knownWrappingKeyAlias
                ),
                defaultContext
            )
        }
        private val unknownKeyPairs = cryptoService.supportedSchemes.keys.associateWith {
            generateKeyPair(schemeMetadata, it.codeName)
        }
        private val zeroBytes = ByteArray(100)
        private val UNSUPPORTED_KEY_SCHEME = CipherSchemeMetadataProvider().COMPOSITE_KEY_TEMPLATE.makeScheme("BC")

        @JvmStatic
        fun derivingSchemes(): List<KeyScheme> =
            deriveSupportedSchemes(schemeMetadata).keys.filter {
                it.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION)
            }

        @JvmStatic
        fun signingSchemes(): List<Arguments> = deriveSupportedSchemes(schemeMetadata)
            .flatMap { entry ->
                entry.value.map { spec -> Arguments.of(entry.key, spec) }
            }


        @JvmStatic
        // TODO move SPHINCS256 tests to a non-critical-path E2E test; just a simple deterministic test is taking
        // 1.3s to do 2 SPHICS256 signatures on a fast laptop
        fun deterministicSignatureSchemes() = listOf(
            EDDSA_ED25519_CODE_NAME, SPHINCS256_CODE_NAME, RSA_CODE_NAME
        ).flatMap { listOf(Arguments.of(false, it), Arguments.of(true, it)) }

        @JvmStatic
        fun ecdsaSchemes() = listOf(
            ECDSA_SECP256K1_CODE_NAME, ECDSA_SECP256R1_CODE_NAME
        ).flatMap { listOf(Arguments.of(false, it), Arguments.of(true, it)) }
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    @Suppress("MaxLineLength")
    fun `should throw IllegalStateException when signing with unknown wrapping key for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        fun verifySign(key: GeneratedWrappedKey, spec: SignatureSpec) {
            assertThrows<IllegalStateException> {
                cryptoService.sign(
                    SigningWrappedSpec(
                        publicKey = key.publicKey,
                        keyMaterialSpec = KeyMaterialSpec(
                            keyMaterial = key.keyMaterial,
                            masterKeyAlias = UUID.randomUUID().toString(),
                            encodingVersion = key.encodingVersion
                        ),
                        keyScheme = scheme,
                        signatureSpec = spec
                    ),
                    UUID.randomUUID().toString().toByteArray(),
                    defaultContext
                )
            }
        }
        verifySign(softAliasedKeys.getValue(scheme), spec)
        verifySign(softFreshKeys.getValue(scheme), spec)
    }


    @ParameterizedTest
    @MethodSource("deterministicSignatureSchemes")
    fun `should generate deterministic signatures for some algorithms`(
        fresh: Boolean,
        codeName: String
    ) {
        val scheme = schemeMetadata.schemes.first { it.codeName == codeName }
        val key = (if (fresh) softFreshKeys else softAliasedKeys).getValue(scheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val signingWrappedSpec = makeSigningWrappedSpec(scheme, key)
        val signedData1stTime = cryptoService.sign(signingWrappedSpec, testData, defaultContext)
        val signedData2ndTime = cryptoService.sign(signingWrappedSpec, testData, defaultContext)
        assertArrayEquals(signedData1stTime, signedData2ndTime)
        val signedZeroArray1stTime = cryptoService.sign(signingWrappedSpec, zeroBytes, defaultContext)
        val signedZeroArray2ndTime = cryptoService.sign(signingWrappedSpec, zeroBytes, defaultContext)
        assertArrayEquals(signedZeroArray1stTime, signedZeroArray2ndTime)
        assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedZeroArray1stTime))
    }

    @ParameterizedTest
    @MethodSource("ecdsaSchemes")
    fun `should generate non deterministic signatures for ECDSA`(
        fresh: Boolean,
        codeName: String
    ) {
        val scheme = schemeMetadata.schemes.first { it.codeName == codeName }
        val key = (if (fresh) softFreshKeys else softAliasedKeys).getValue(scheme)
        val testData = UUID.randomUUID().toString().toByteArray()
        val signingWrappedSpec = makeSigningWrappedSpec(scheme, key)
        val signedData1stTime = cryptoService.sign(signingWrappedSpec, testData, defaultContext)
        val signedData2ndTime = cryptoService.sign(signingWrappedSpec, testData, defaultContext)
        assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))
        val signedZeroArray1stTime = cryptoService.sign(signingWrappedSpec, zeroBytes, defaultContext)
        val signedZeroArray2ndTime = cryptoService.sign(signingWrappedSpec, zeroBytes, defaultContext)
        assertNotEquals(OpaqueBytes(signedZeroArray1stTime), OpaqueBytes(signedZeroArray2ndTime))
    }

    private fun makeSigningWrappedSpec(
        scheme: KeyScheme,
        key: GeneratedWrappedKey
    ): SigningWrappedSpec {
        val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
        return SigningWrappedSpec(
            publicKey = key.publicKey,
            keyMaterialSpec = KeyMaterialSpec(key.keyMaterial, knownWrappingKeyAlias, key.encodingVersion),
            keyScheme = scheme,
            signatureSpec = signatureSpec
        )
    }


    @Test
    fun `should generate RSA key pair`() {
        val scheme = schemeMetadata.findKeyScheme(RSA_CODE_NAME)
        assertEquals("RSA", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("RSA", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `should generate ECDSA key pair with secp256k1 curve`() {
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
    fun `should generate ECDSA key pair with secp256r1 curve`() {
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
    fun `should generate EdDSA key pair with ED25519 curve`() {
        val scheme = schemeMetadata.findKeyScheme(EDDSA_ED25519_CODE_NAME)
        assertEquals("Ed25519", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("Ed25519", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `should generate SPHINCS-256 key pair`() {
        val scheme = schemeMetadata.findKeyScheme(SPHINCS256_CODE_NAME)
        assertEquals("SPHINCS-256", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("SPHINCS-256", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `should generate SM2 key pair`() {
        fun assertPublicKey(publicKey: PublicKey) {
            assertInstanceOf(ECKey::class.java, publicKey)
            assertEquals("EC", publicKey.algorithm)
            assertEquals(ECNamedCurveTable.getParameterSpec("sm2p256v1"), (publicKey as ECKey).parameters)
        }

        val scheme = schemeMetadata.findKeyScheme(SM2_CODE_NAME)
        assertPublicKey(softAliasedKeys.getValue(scheme).publicKey)
        assertPublicKey(softFreshKeys.getValue(scheme).publicKey)
    }

    //
    @Test
    fun `should generate GOST3410_GOST3411 key pair`() {
        val scheme = schemeMetadata.findKeyScheme(GOST3410_GOST3411_CODE_NAME)
        assertEquals("GOST3410", softAliasedKeys.getValue(scheme).publicKey.algorithm)
        assertEquals("GOST3410", softFreshKeys.getValue(scheme).publicKey.algorithm)
    }

    @Test
    fun `should throw IllegalArgumentException when generating key pair with unsupported key scheme`() {
        assertThrows<IllegalArgumentException> {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(UNSUPPORTED_KEY_SCHEME, UUID.randomUUID().toString(), knownWrappingKeyAlias),
                defaultContext
            )
        }
        assertThrows<IllegalArgumentException> {
            cryptoService.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = UNSUPPORTED_KEY_SCHEME,
                    alias = null,
                    masterKeyAlias = knownWrappingKeyAlias
                ),
                defaultContext
            )
        }
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `should fail to use aliased key generated for another wrapping key for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        val anotherWrappingKey = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(anotherWrappingKey, true, defaultContext)
        val testData = UUID.randomUUID().toString().toByteArray()
        val key = softAliasedKeys.getValue(scheme)
        assertThrows<Throwable> {
            cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = anotherWrappingKey,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = spec
                ),
                testData,
                defaultContext
            )
        }
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `should fail to use fresh key generated for another wrapping key for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
    ) {
        val anotherWrappingKey = UUID.randomUUID().toString()
        cryptoService.createWrappingKey(anotherWrappingKey, true, defaultContext)
        val testData = UUID.randomUUID().toString().toByteArray()
        val key = softFreshKeys.getValue(scheme)
        assertThrows<Throwable> {
            cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = anotherWrappingKey,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = spec
                ),
                testData,
                defaultContext
            )
        }
    }


    @Test
    fun `wrapping keys appear at the expected times using keyalias`() {
        val expected1 = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val expected2 = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val alias1 = UUID.randomUUID().toString()
        val alias2 = UUID.randomUUID().toString()
        val info1 = WrappingKeyInfo(
            WRAPPING_KEY_ENCODING_VERSION,
            expected1.algorithm,
            rootWrappingKey.wrap(expected1)
        )
        val info2 = WrappingKeyInfo(
            WRAPPING_KEY_ENCODING_VERSION,
            expected2.algorithm,
            rootWrappingKey.wrap(expected2)
        )
        val key1Missing = wrappingKeyCache.getIfPresent(alias1)
        assertNull(key1Missing)
        val key2Missing = wrappingKeyCache.getIfPresent(alias2)
        assertNull(key2Missing)

        wrappingKeyStore.saveWrappingKey(alias1, info1)
        wrappingKeyStore.saveWrappingKey(alias2, info2)

        val key1StillMissing = wrappingKeyCache.getIfPresent(alias1)
        assertNull(key1StillMissing)
        val key2StillMissing = wrappingKeyCache.getIfPresent(alias2)
        assertNull(key2StillMissing)

        val scheme = cryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first
        cryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key1", alias1), emptyMap())
        val key1Found = wrappingKeyCache.getIfPresent(alias1)
        assertEquals(expected1, key1Found)
        val key2AgainStillMissing = wrappingKeyCache.getIfPresent(alias2)
        assertNull(key2AgainStillMissing)

        cryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key2", alias2), emptyMap())
        val key2Found = wrappingKeyCache.getIfPresent(alias2)
        assertEquals(expected2, key2Found)
        assertNotEquals(key1Found, key2Found)

        val key1FoundLater = wrappingKeyCache.getIfPresent(alias1)
        assertEquals(expected1, key1FoundLater)
        
        assertThat(wrappingKeyStore.findCounter[alias1]).isEqualTo(1)
        assertThat(wrappingKeyStore.findCounter[alias2]).isEqualTo(1)
    }

    @Test
    fun `getWrappingKey should throw IllegalArgumentException when encoding version is not recognised`() {
        val alias = UUID.randomUUID().toString()
        wrappingKeyStore.saveWrappingKey(
            alias, WrappingKeyInfo(
                WRAPPING_KEY_ENCODING_VERSION + 1,
                knownWrappingKey.algorithm,
                rootWrappingKey.wrap(knownWrappingKey)
            )
        )
        assertThrows<IllegalArgumentException> {
            cryptoService.getWrappingKey(alias)
        }
    }


    @Test
    fun `getWrappingKey should throw IllegalArgumentException when key algorithm does not match master key`() {
        val alias = UUID.randomUUID().toString()
        wrappingKeyStore.saveWrappingKey(
            alias, WrappingKeyInfo(
                WRAPPING_KEY_ENCODING_VERSION,
                knownWrappingKey.algorithm + "!",
                rootWrappingKey.wrap(knownWrappingKey)
            )
        )
        assertThrows<IllegalArgumentException> {
            cryptoService.getWrappingKey(alias)
        }
    }


    @Test
    fun `getWrappingKey should throw IllegalStateException when wrapping key is not found`() {
        val alias = UUID.randomUUID().toString()
        assertThrows<IllegalStateException> {
            cryptoService.getWrappingKey(alias)
        }
    }

    @Test
    fun `wrapping key store can find keys that have been stored`() {
        val storeAlias = UUID.randomUUID().toString()
        val unknownAlias = UUID.randomUUID().toString()
        assertNull(wrappingKeyStore.findWrappingKey(storeAlias))
        assertNull(wrappingKeyStore.findWrappingKey(unknownAlias))
        wrappingKeyStore.saveWrappingKey(storeAlias, WrappingKeyInfo(1, "t", byteArrayOf()))
        assertNotNull(wrappingKeyStore.findWrappingKey(storeAlias))
        assertNull(wrappingKeyStore.findWrappingKey(unknownAlias))
    }

    @Test
    fun `Should fail unwrap if master key alias is empty`() {
        assertThrows<java.lang.IllegalArgumentException> {
            cryptoService.createWrappingKey("", true, mapOf())
        }
        assertThrows<java.lang.IllegalStateException> {
            cryptoService.getWrappingKey("")
        }
    }
    /*
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
                    stableKeyPair.signingService.deriveSharedSecret(
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
        val salt = ByteArray(DigestFactory.getDigest("SHA-256").digestSize).apply {
            schemeMetadata.secureRandom.nextBytes(this)
        }
        val plainText = "Hello MGM!".toByteArray()
        val cipherText = ephemeralEncryptor.encrypt(
            salt = salt,
            otherPublicKey = stableKeyPair.publicKey,
            plainText = plainText,
            aad = null
        )
        val decryptedPlainTex = stableDecryptor.decrypt(
            tenantId = tenantId,
            salt = salt,
            publicKey = stableKeyPair.publicKey,
            otherPublicKey = cipherText.publicKey,
            cipherText = cipherText.cipherText,
            aad = null
        )
        assertArrayEquals(plainText, decryptedPlainTex)
    }

     */

}

