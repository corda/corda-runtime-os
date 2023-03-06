package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.KeySchemeCapability
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.SoftKeyMap
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.GOST3410_GOST3411_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SM2_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECKey
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests are combined to improve performance as it takes a lot of time to generate keys and considering the number
 * of permutations when especially running tests for customized signature specs (over 70) it makes sense
 * trying to generate keys once and run all related tests
 */
class SoftCryptoServiceOperationsTests {
    companion object {
        private val zeroBytes = ByteArray(100)
        private val UNSUPPORTED_KEY_SCHEME = CipherSchemeMetadataProvider().COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
        private lateinit var coordinatorFactory: TestLifecycleCoordinatorFactoryImpl
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var platformDigestService: PlatformDigestService
        private lateinit var tenantId: String
        private lateinit var category: String
        private lateinit var wrappingKeyAlias: String
        private lateinit var cryptoService: CryptoService
        private lateinit var softAliasedKeys: Map<KeyScheme, GeneratedWrappedKey>
        private lateinit var softFreshKeys: Map<KeyScheme, GeneratedWrappedKey>
        private lateinit var unknownKeyPairs: Map<KeyScheme, KeyPair>
        private lateinit var masterKey: WrappingKey
        private lateinit var wrappingKeyStore: TestWrappingKeyStore
        private lateinit var keyMap: SoftKeyMap
        private lateinit var wrappingKeyMap: SoftWrappingKeyMap
        private lateinit var wrapping: SoftPrivateKeyWrapping

        @JvmStatic
        @BeforeAll
        fun setup() {
            coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
            schemeMetadata = CipherSchemeMetadataImpl()
            masterKey = WrappingKey.generateWrappingKey(schemeMetadata)
            platformDigestService = PlatformDigestServiceImpl(schemeMetadata)
            tenantId = UUID.randomUUID().toString()
            category = CryptoConsts.Categories.LEDGER
            wrappingKeyStore = TestWrappingKeyStore(coordinatorFactory).also {
                it.start()
                eventually { assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status) }
            }
            wrappingKeyAlias = UUID.randomUUID().toString()
            wrappingKeyMap = CachingSoftWrappingKeyMap(
                SoftCacheConfig(0, 0),
                wrappingKeyStore,
                masterKey
            )
            wrapping = DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
            keyMap = TransientSoftKeyMap(wrapping)
            cryptoService = SoftCryptoService(
                keyMap,
                wrappingKeyMap,
                schemeMetadata,
                platformDigestService
            )
            cryptoService.createWrappingKey(wrappingKeyAlias, true, emptyMap())
            softAliasedKeys = cryptoService.supportedSchemes.keys.associateWith {
                cryptoService.generateKeyPair(
                    KeyGenerationSpec(
                        keyScheme = it,
                        alias = UUID.randomUUID().toString(),
                        masterKeyAlias = wrappingKeyAlias
                    ),
                    mapOf(
                        CRYPTO_TENANT_ID to tenantId,
                        CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                    )
                ) as GeneratedWrappedKey
            }
            softFreshKeys = cryptoService.supportedSchemes.keys.associateWith {
                cryptoService.generateKeyPair(
                    KeyGenerationSpec(
                        keyScheme = it,
                        alias = null,
                        masterKeyAlias = wrappingKeyAlias
                    ),
                    mapOf(
                        CRYPTO_TENANT_ID to tenantId,
                        CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                    )
                ) as GeneratedWrappedKey
            }
            unknownKeyPairs = cryptoService.supportedSchemes.keys.associateWith {
                generateKeyPair(schemeMetadata, it.codeName)
            }
        }

        @JvmStatic
        fun derivingSchemes(): List<KeyScheme> =
            cryptoService.supportedSchemes.keys.filter {
                it.canDo(KeySchemeCapability.SHARED_SECRET_DERIVATION)
            }

        @JvmStatic
        fun signingSchemes(): List<Arguments> {
            val list = mutableListOf<Arguments>()
            cryptoService.supportedSchemes.forEach { entry ->
                entry.value.forEach { spec ->
                    list.add(Arguments.of(entry.key, spec))
                }
            }
            return list
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
    @MethodSource("signingSchemes")
    @Suppress("MaxLineLength")
    fun `SoftCryptoService should throw IllegalStateException when signing with unknown wrapping key for all supported schemes`(
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
                    mapOf(
                        CRYPTO_TENANT_ID to tenantId
                    )
                )
            }
        }
        verifySign(softAliasedKeys.getValue(scheme), spec)
        verifySign(softFreshKeys.getValue(scheme), spec)
    }

    @Test
    fun `SoftCryptoService should generate deterministic signatures for EdDSA, SPHINCS-256 and RSA`() {
        fun verifySign(key: GeneratedWrappedKey, scheme: KeyScheme) {
            val testData = UUID.randomUUID().toString().toByteArray()
            val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
            val signedData1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedData2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            assertArrayEquals(signedData1stTime, signedData2ndTime)
            val signedZeroArray1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                zeroBytes,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedZeroArray2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
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
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedData2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                testData,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            assertNotEquals(OpaqueBytes(signedData1stTime), OpaqueBytes(signedData2ndTime))
            val signedZeroArray1stTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                zeroBytes,
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
            val signedZeroArray2ndTime = cryptoService.sign(
                SigningWrappedSpec(
                    publicKey = key.publicKey,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = key.keyMaterial,
                        masterKeyAlias = wrappingKeyAlias,
                        encodingVersion = key.encodingVersion
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
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
                    masterKeyAlias = wrappingKeyAlias
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
                    masterKeyAlias = wrappingKeyAlias
                ),
                mapOf(
                    CRYPTO_TENANT_ID to tenantId,
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `SoftCryptoService should fail to use aliased key generated for another wrapping key for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
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
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("signingSchemes")
    fun `SoftCryptoService should fail to use fresh key generated for another wrapping key for all supported schemes`(
        scheme: KeyScheme,
        spec: SignatureSpec
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
                mapOf(
                    CRYPTO_TENANT_ID to tenantId
                )
            )
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

