package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.SigningKeyStatus
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.impl.infra.TestWrappingRepository
import net.corda.crypto.softhsm.impl.infra.makeShortHashCache
import net.corda.crypto.softhsm.impl.infra.makeSoftCryptoService
import net.corda.crypto.softhsm.impl.infra.makeTenantInfoService
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.X25519_CODE_NAME
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.PublicKey
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException
import javax.persistence.QueryTimeoutException
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* SoftCryptoService tests that do not require wrapping keys */
class SoftCryptoServiceGeneralTests {
    private val schemeMetadata = CipherSchemeMetadataImpl()
    private val UNSUPPORTED_SIGNATURE_SCHEME = CipherSchemeMetadataProvider().COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
    private val cryptoRepositoryWrapping = TestWrappingRepository()
    private val sampleWrappingKeyInfo = WrappingKeyInfo(1, "AES", byteArrayOf(), 1, "root")
    val defaultContext =
        mapOf(CRYPTO_TENANT_ID to UUID.randomUUID().toString(), CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER)
    private val service = makeSoftCryptoService(
        wrappingRepository = cryptoRepositoryWrapping,
        schemeMetadata = schemeMetadata,
        rootWrappingKey = mock(),
    )

    companion object {
        @JvmStatic
        fun keyOrders() = KeyOrderBy.values()

        private val tenantId = UUID.randomUUID().toString()

        private val masterKeyAlias = "root"

        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val signingKeyInfo0 = mock<SigningKeyInfo> {
            on { fullId }.thenReturn(fullKeyId0)
            on { id }.thenReturn(shortKeyId0)
        }
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)
        val signingKeyInfo1 = mock<SigningKeyInfo> {
            on { fullId }.thenReturn(fullKeyId1)
            on { id }.thenReturn(shortKeyId1)
        }
    }

    @Test
    fun `Should throw IllegalStateException when wrapping key alias exists and failIfExists is true`() {
        val alias = "stuff"
        cryptoRepositoryWrapping.keys[alias] = sampleWrappingKeyInfo
        val exception = assertThrows<IllegalStateException> {
            service.createWrappingKey(alias, true, emptyMap())
        }
        assertThat(exception.message).contains("There is an existing key with the alias")
        assertThat(cryptoRepositoryWrapping.keys[alias]).isEqualTo(sampleWrappingKeyInfo)
    }

    @Test
    fun `Should not generate new master key when master alias exists and failIfExists is false`() {
        cryptoRepositoryWrapping.keys["stuff2"] = sampleWrappingKeyInfo
        service.createWrappingKey("stuff2", false, emptyMap())
        assertThat(cryptoRepositoryWrapping.keys["stuff2"]).isEqualTo(sampleWrappingKeyInfo)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw IllegalArgumentException when generating key pair and signature scheme is not supported`() {
        cryptoRepositoryWrapping.keys["stuff3"] = sampleWrappingKeyInfo
        val exception = assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    alias = "whatever",
                    wrappingKeyAlias = "stuff3",
                ),
                defaultContext
            )
        }
        assertThat(exception.message).contains("Unsupported key scheme")
    }

    @Test
    fun `Should throw IllegalArgumentException when signing empty data array`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = service.supportedSchemes.keys.first { it.codeName == ECDSA_SECP256R1_CODE_NAME },
                    signatureSpec = SignatureSpecs.ECDSA_SHA256,
                    category = CryptoConsts.Categories.LEDGER
                ),
                ByteArray(0),
                defaultContext
            )
        }
        assertThat(exception.message).contains("Signing of an empty array is not permitted")
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using unsupported scheme`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    signatureSpec = SignatureSpecs.ECDSA_SHA256,
                    category = CryptoConsts.Categories.LEDGER
                ),
                ByteArray(0),
                defaultContext
            )
        }
        assertThat(exception.message).contains("Signing of an empty array is not permitted")
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using scheme which does not support signing`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = service.supportedSchemes.keys.first { it.codeName == X25519_CODE_NAME },
                    signatureSpec = SignatureSpecs.EDDSA_ED25519,
                    category = CryptoConsts.Categories.LEDGER
                ),
                ByteArray(0),
                defaultContext
            )
        }
        assertThat(exception.message).contains("Signing of an empty array is not permitted")
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key and spec is not SharedSecretWrappedSpec`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(mock(), defaultContext)
        }
        assertThat(exception.message).contains("The service supports only class net.corda.crypto.cipher.suite.SharedSecretWrappedSpec")
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using scheme which does not support it`() {
        val keyScheme = service.supportedSchemes.keys.first { it.codeName == EDDSA_ED25519_CODE_NAME }
        val myKeyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val exception = assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(
                SharedSecretWrappedSpec(
                    publicKey = myKeyPair.public,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = keyScheme,
                    otherPublicKey = otherKeyPair.public
                ),
                defaultContext
            )
        }
        assertThat(exception.message).contains("must support the Diffie–Hellman key agreement")
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using keys with different schemes`() {
        val keyScheme = service.supportedSchemes.keys.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        val myKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256K1_CODE_NAME)
        val exception = assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(
                SharedSecretWrappedSpec(
                    publicKey = myKeyPair.public,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = keyScheme,
                    otherPublicKey = otherKeyPair.public
                ),
                defaultContext
            )
        }
        assertThat(exception.message).contains("The keys must use the same key scheme, ")
    }

    @Test
    fun `SoftCryptoService should require wrapping key`() {
        assertThat(service.extensions).contains(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)
    }


    @Test
    fun `SoftCryptoService should not support key deletion`() {
        assertThat(service.extensions).doesNotContain(CryptoServiceExtensions.DELETE_KEYS)
    }


    @Test
    fun `SoftCryptoService should support at least one schemes defined in cipher suite`() {
        assertThat(service.supportedSchemes).isNotEmpty
        assertTrue(service.supportedSchemes.any {
            schemeMetadata.schemes.contains(it.key)
        })
    }

    @Test
    fun `Should throw CryptoException when a DB query fails with QueryTimeoutException`() {
        val cryptoServiceExploding = makeSoftCryptoService(wrappingRepository = mock() {
            on { findKey(any()) } doThrow QueryTimeoutException()
        })
        val exception = assertThrows<CryptoException> {
            cryptoServiceExploding.createWrappingKey("foo", true, emptyMap())
        }
        assertThat(exception.message).contains("Calling createWrappingKey findKey failed in a potentially recoverable way")
    }

    @Test
    fun `Should throw original exception when a DB query fails with IllegalStateException`() {
        val cryptoServiceExploding = makeSoftCryptoService(wrappingRepository = mock() {
            on { findKey(any()) } doThrow IllegalStateException()
        })
        val exception = assertThrows<IllegalStateException> {
            cryptoServiceExploding.createWrappingKey("foo", true, emptyMap())
        }
        assertNull(exception.message)
    }


    @Test
    fun `Should pass all parameters to cache for lookup function`() {
        val skip = 17
        val take = 21
        val orderBy: KeyOrderBy = KeyOrderBy.ALIAS
        val category: String = CryptoConsts.Categories.TLS
        val schemeCodeName: String = UUID.randomUUID().toString()
        val alias: String = UUID.randomUUID().toString()
        val masterKeyAlias: String = UUID.randomUUID().toString()
        val createdAfter: Instant = Instant.now().plusSeconds(-5)
        val createdBefore: Instant = Instant.now()
        val store = mock<SigningRepository> {
            on { query(any(), any(), any(), any()) } doReturn emptyList()
        }
        val filter = mapOf(
            CryptoConsts.SigningKeyFilters.CATEGORY_FILTER to category,
            CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER to schemeCodeName,
            CryptoConsts.SigningKeyFilters.ALIAS_FILTER to alias,
            CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER to masterKeyAlias,
            CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER to createdAfter.toString(),
            CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER to createdBefore.toString()
        )
        val cryptoServiceWithMockSigningRepository = makeSoftCryptoService(
            wrappingRepository = cryptoRepositoryWrapping,
            signingRepository = store,
            schemeMetadata = schemeMetadata,
            rootWrappingKey = mock(),
        )
        val result = cryptoServiceWithMockSigningRepository.querySigningKeys(tenantId, skip, take, orderBy, filter)
        assertThat(result).isNotNull
        assertThat(result.size).isEqualTo(0)
        verify(store, times(1)).query(skip, take, SigningKeyOrderBy.ALIAS, filter)
    }


    @ParameterizedTest
    @MethodSource("keyOrders")
    fun `Should pass order by to lookup function`(orderBy: KeyOrderBy) {
        val skip = 17
        val take = 21
        val tenantId: String = UUID.randomUUID().toString()
        val repo = mock<SigningRepository> {
            on { query(any(), any(), any(), any()) } doReturn emptyList()
        }
        val cryptoServiceWithMockSigningRepository = makeSoftCryptoService(
            wrappingRepository = cryptoRepositoryWrapping,
            signingRepository = repo,
            schemeMetadata = schemeMetadata,
            rootWrappingKey = mock(),
        )
        val filter = emptyMap<String, String>()
        val result = cryptoServiceWithMockSigningRepository.querySigningKeys(
            tenantId,
            skip,
            take,
            orderBy,
            filter
        )
        assertThat(result).isNotNull
        assertThat(result.size).isEqualTo(0)
        verify(repo, times(1)).query(
            skip,
            take,
            SigningKeyOrderBy.valueOf(orderBy.toString()),
            filter
        )
    }


    @Test
    fun `Should throw original exception failing signing`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val publicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val data = ByteArray(2)
        val signatureSpec = SignatureSpecImpl("NONE")
        val service = makeSoftCryptoService(
            wrappingRepository = cryptoRepositoryWrapping,
            schemeMetadata = schemeMetadata,
            rootWrappingKey = mock(),
            signingRepository = repo
        )
        val thrown = Assertions.assertThrows(exception::class.java) {
            service.sign(
                tenantId = tenantId,
                publicKey = publicKey,
                signatureSpec = signatureSpec,
                data = data,
                context = emptyMap()
            )
        }
        Assertions.assertSame(exception, thrown)
        assertThat(thrown.message).isEmpty()
        Mockito.verify(repo, times(1)).findKey(any<PublicKey>())
    }

    @Test
    fun `Should throw IllegalArgumentException when key is not found for signing`() {
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doReturn null
        }
        val cryptoService = makeSoftCryptoService(signingRepository = repo)
        val publicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        val exception = assertThrows<IllegalArgumentException> {
            cryptoService.sign(
                tenantId = tenantId,
                publicKey = publicKey,
                signatureSpec = SignatureSpecImpl("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertThat(exception.message).contains("The public key")
        assertThat(exception.message).contains("was not found")
    }

    @Test
    fun `Should close the repo after use`() {
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doReturn null
        }
        val cryptoService = makeSoftCryptoService(signingRepository = repo)
        val publicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        assertThrows<IllegalArgumentException> {
            cryptoService.sign(
                tenantId = tenantId,
                publicKey = publicKey,
                signatureSpec = SignatureSpecImpl("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }

        verify(repo).close()
    }

    private fun mockDigestService() = mock<PlatformDigestService> {
        on { hash(any<ByteArray>(), any()) } doReturn SecureHashUtils.randomSecureHash()
    }

    private fun makeCache(): Cache<ShortHashCacheKey, SigningKeyInfo> =
        Caffeine.newBuilder()
            .expireAfterAccess(3600, TimeUnit.MINUTES)
            .maximumSize(3).build()

    @Test
    fun `Should throw original exception failing derivation`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val cryptoService = makeSoftCryptoService(signingRepository = repo)
        val thrown = Assertions.assertThrows(exception::class.java) {
            cryptoService.deriveSharedSecret(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                otherPublicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                context = emptyMap()
            )
        }
        Assertions.assertSame(exception, thrown)
        assertThat(thrown.message).isEmpty()
        verify(repo, times(1)).findKey(any<PublicKey>())
    }

    @Test
    fun `Should throw IllegalArgumentException when key is not found for deriving`() {
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doReturn null
        }
        val cryptoService = makeSoftCryptoService(signingRepository = repo)
        val exception = assertThrows<IllegalArgumentException> {
            cryptoService.deriveSharedSecret(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                otherPublicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                context = emptyMap()
            )
        }
        assertThat(exception.message).contains("The public key")
        assertThat(exception.message).contains("was not found")
    }

    @Test
    fun `Should throw KeyAlreadyExistsException when generating key with existing alias`() {
        val existingKey = SigningKeyInfo(
            id = ShortHash.of("0123456789AB"),
            fullId = parseSecureHash("SHA-256:0123456789ABCDEF"),
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            alias = "alias1",
            hsmAlias = null,
            publicKey = mock(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            wrappingKeyAlias = masterKeyAlias,
            externalId = null,
            schemeCodeName = ECDSA_SECP256R1_CODE_NAME,
            encodingVersion = 1,
            hsmId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val tenantId = UUID.randomUUID().toString()
        val repo = mock<SigningRepository> {
            on { findKey(ArgumentMatchers.anyString()) } doReturn existingKey
        }
        val mockAssoicationInfo = mock<HSMAssociationInfo> {
            on { masterKeyAlias } doReturn "alias0"
        }
        val tenantInfoService = mock<TenantInfoService> {
            on { lookup(eq(tenantId), any()) } doReturn mockAssoicationInfo
        }
        val cryptoService = makeSoftCryptoService(signingRepository = repo, tenantInfoService = tenantInfoService)
        val exception = assertThrows<KeyAlreadyExistsException> {
            cryptoService.generateKeyPair(
                tenantId = tenantId,
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                externalId = null,
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThat(exception.message).contains("The key with alias")
        assertThat(exception.message).contains("already exists for tenant")
        val exception2 = assertThrows<KeyAlreadyExistsException> {
            cryptoService.generateKeyPair(
                tenantId = tenantId,
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThat(exception2.message).contains("The key with alias")
        assertThat(exception2.message).contains("already exists for tenant")
    }

    @Test
    fun `Should throw original when failing key generation with alias`() {
        val exception = RuntimeException("")
        val tenantId = UUID.randomUUID().toString()
        val repo = mock<SigningRepository> {
            on { findKey(ArgumentMatchers.anyString()) } doThrow exception
        }
        val mockAssoicationInfo = mock<HSMAssociationInfo> {
            on { masterKeyAlias } doReturn "alias1"
        }
        val tenantInfoService = mock<TenantInfoService> {
            on { lookup(eq(tenantId), any()) } doReturn mockAssoicationInfo
        }
        val cryptoService = makeSoftCryptoService(signingRepository = repo,
            tenantInfoService = tenantInfoService)
        var thrown = Assertions.assertThrows(exception::class.java) {
            cryptoService.generateKeyPair(
                tenantId = tenantId,
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThat(thrown.message).isEmpty()
        Assertions.assertSame(exception, thrown)
        thrown = Assertions.assertThrows(exception::class.java) {
            cryptoService.generateKeyPair(
                tenantId = tenantId,
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThat(thrown.message).isEmpty()
        Assertions.assertSame(exception, thrown)
        verify(repo, times(2)).findKey(ArgumentMatchers.anyString())
    }

    @Test
    @Suppress("ComplexMethod", "MaxLineLength")
    fun `Should save generated key with alias`() {
        val generatedKey = GeneratedWrappedKey(
            publicKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            keyMaterial = byteArrayOf(42),
            encodingVersion = 1
        )
        val expectedAlias = UUID.randomUUID().toString()
        val signingKeyInfo = mock<SigningKeyInfo> {
            on { publicKey } doReturn generatedKey.publicKey
        }
        val repo = mock<SigningRepository> {
            on { savePrivateKey(any()) } doReturn signingKeyInfo
        }
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        val wrappingRepository = mock<WrappingRepository>() {
            on { findKey(any()) } doReturn sampleWrappingKeyInfo
        }
        val service = object : SoftCryptoService(
            wrappingRepositoryFactory = { wrappingRepository },
            signingRepositoryFactory = { repo },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = "root",
            unmanagedWrappingKeys = mapOf("root" to WrappingKeyImpl.generateWrappingKey(schemeMetadata)),
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            wrappingKeyCache = null,
            privateKeyCache = null,
            shortHashCache = makeShortHashCache(),
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyFactory = { WrappingKeyImpl.generateWrappingKey(it) },
            tenantInfoService = makeTenantInfoService(masterKeyAlias)
        ) {
            override fun generateKeyPair(spec: KeyGenerationSpec, context: Map<String, String>): GeneratedWrappedKey =
                generatedKey
        }
        var result = service.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            scheme = scheme,
            alias = expectedAlias
        ).publicKey
        assertThat(generatedKey.publicKey).isEqualTo(result)
        val expectedExternalId = UUID.randomUUID().toString()
        result = service.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            externalId = expectedExternalId,
            scheme = scheme,
            alias = expectedAlias
        ).publicKey
        assertThat(generatedKey.publicKey).isEqualTo(result)
        verify(repo, times(1)).savePrivateKey(
            argThat {
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == null &&
                        keyScheme == scheme
            }
        )
        verify(repo, times(1)).savePrivateKey(
            argThat {
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == expectedExternalId &&
                        keyScheme == scheme
            }
        )
    }

    @Test
    fun `repository can correctly looks up a signing key by short ids`() {
        val hashA = ShortHash.of("0123456789AB")
        val hashB = ShortHash.of("123456789ABC")
        val keys = listOf(hashA, hashB)
        val mockCachedKey = mock<SigningKeyInfo> { on { id } doReturn hashA }
        val queryCap = argumentCaptor<Iterable<ShortHashCacheKey>>()
        val cache = mock<Cache<ShortHashCacheKey, SigningKeyInfo>> {
            on { getAllPresent(queryCap.capture()) } doReturn mapOf(
                ShortHashCacheKey("tenant", hashA) to mockCachedKey
            )
        }
        val keyIdsCap = argumentCaptor<Set<ShortHash>>()
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn mock() }
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyShortHashes(keyIdsCap.capture()) } doReturn setOf(signingKeyInfo)
        }

        val cryptoService = makeSoftCryptoService(signingRepository = repo, shortHashCache = cache)
        cryptoService.lookupSigningKeysByPublicKeyShortHash("tenant", keys)

        val cacheKeys = setOf(ShortHashCacheKey("tenant", hashA), ShortHashCacheKey("tenant", hashB))
        queryCap.allValues.single().forEach {
            assertThat(it in cacheKeys)
        }
        assertThat(keyIdsCap.allValues.single()).isEqualTo(setOf(hashB))
    }

    @Test
    fun `repository correctly looks up a signing key by full ids when needs both cache and database`() {
        val hashA = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "0123456789AB".toByteArray())
        val hashB = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "123456789ABC".toByteArray())
        val shortA = ShortHash.of(hashA)
        val shortB = ShortHash.of(hashB)
        val keys = listOf(hashA, hashB)
        val queryCap = argumentCaptor<Iterable<ShortHashCacheKey>>()
        val mockCachedKey = mock<SigningKeyInfo> { on { fullId } doReturn hashA }
        val cache = mock<Cache<ShortHashCacheKey, SigningKeyInfo>> {
            on { getAllPresent(queryCap.capture()) } doReturn mapOf(
                ShortHashCacheKey("tenant", shortA) to mockCachedKey
            )
        }
        val fullIdsCap = argumentCaptor<Set<SecureHash>>()
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn mock() }
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyHashes(fullIdsCap.capture()) } doReturn setOf(signingKeyInfo)
        }

        val cryptoService = makeSoftCryptoService(signingRepository = repo, shortHashCache = cache)
        cryptoService.lookupSigningKeysByPublicKeyHashes("tenant", keys)

        val cacheKeys = setOf(ShortHashCacheKey("tenant", shortA), ShortHashCacheKey("tenant", shortB))
        queryCap.allValues.single().forEach {
            assertThat(it in cacheKeys)
        }
        assertThat(fullIdsCap.allValues.single()).isEqualTo(setOf(hashB))
    }

    @ParameterizedTest
    @CsvSource("0,false", "1,false", "2,false", "0,true", "1,true", "2,true")
    fun `lookup returns requested keys from cache and db`(
        keysInCache: Int,
        longHashes: Boolean,
    ) {
        val shortHashCaptor = argumentCaptor<Set<ShortHash>>()
        val hashCaptor = argumentCaptor<Set<SecureHash>>()
        val mockDbResults = if (keysInCache == 0) setOf(signingKeyInfo0, signingKeyInfo1) else setOf(signingKeyInfo1)

        val repo = if (longHashes) (mock<SigningRepository> {
            on { lookupByPublicKeyHashes(hashCaptor.capture()) }.thenReturn(mockDbResults)
        }) else (mock<SigningRepository> {
            on { lookupByPublicKeyShortHashes(shortHashCaptor.capture()) }.thenReturn(mockDbResults)
        })
        val cache = makeCache()
        var repoCount = 0
        val cryptoService = SoftCryptoService(
            wrappingRepositoryFactory = { mock<WrappingRepository>() },
            signingRepositoryFactory = {
                repoCount++
                repo
            },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = "root",
            unmanagedWrappingKeys = mapOf("root" to mock<WrappingKey>()),
            digestService = mockDigestService(),
            wrappingKeyCache = null,
            privateKeyCache = null,
            shortHashCache = cache,
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            tenantInfoService = makeTenantInfoService()
        )
        if (keysInCache >= 1) populateShortHashCache(cache, shortKeyId0, fullKeyId0)
        if (keysInCache >= 2) populateShortHashCache(cache, shortKeyId1, fullKeyId1)
        fun doLookup() = if (longHashes)
            cryptoService.lookupSigningKeysByPublicKeyHashes(tenantId, listOf(fullKeyId0, fullKeyId1))
        else
            cryptoService.lookupSigningKeysByPublicKeyShortHash(tenantId, listOf(shortKeyId0, shortKeyId1))

        val r = doLookup()
        Assertions.assertEquals(
            setOf(fullKeyId0, fullKeyId1).map { it.toString() }.toSet(),
            r.map { it.fullId.toString() }.toSet()
        )
        assertThat(repoCount).isEqualTo(if (keysInCache == 2) 0 else 1)
        if (longHashes) {
            if (keysInCache == 0) Assertions.assertEquals(setOf(fullKeyId0, fullKeyId1), hashCaptor.firstValue)
            if (keysInCache == 1) Assertions.assertEquals(setOf(fullKeyId1), hashCaptor.firstValue)
        } else {
            if (keysInCache == 0) Assertions.assertEquals(setOf(shortKeyId0, shortKeyId1), shortHashCaptor.firstValue)
            if (keysInCache == 1) Assertions.assertEquals(setOf(shortKeyId1), shortHashCaptor.firstValue)
        }
        // looking again should result in no more database access
        val r2 = doLookup()
        assertThat(r).isEqualTo(r2)
        assertThat(repoCount).isEqualTo(if (keysInCache == 2) 0 else 1)
    }

    @Test
    fun `lookupSigningKeysByPublicKeyHashes will not return clashed keys on short key id`() {
        val mockDbResults = setOf(signingKeyInfo0)
        val requestedFullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111112")
        val hashCaptor = argumentCaptor<Set<SecureHash>>()
        val cache = makeShortHashCache()
        var repoCount = 0
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyHashes(hashCaptor.capture()) }.thenReturn(mockDbResults)
        }
        populateShortHashCache(cache, shortKeyId0, fullKeyId0)
        val cryptoService = SoftCryptoService(
            wrappingRepositoryFactory = { mock<WrappingRepository>() },
            signingRepositoryFactory = {
                repoCount++
                repo
            },
            schemeMetadata = schemeMetadata,
            defaultUnmanagedWrappingKeyName = "root",
            unmanagedWrappingKeys = mapOf("root" to mock<WrappingKey>()),
            digestService = mockDigestService(),
            wrappingKeyCache = null,
            privateKeyCache = null,
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            shortHashCache = cache,
            tenantInfoService = makeTenantInfoService()
        )
        val lookedUpByFullKeyIdsKeys =
            cryptoService.lookupSigningKeysByPublicKeyHashes(tenantId, listOf(requestedFullKeyId))
        Assertions.assertEquals(0, lookedUpByFullKeyIdsKeys.size)

        // since we could not find anything in the cache which is
        // suitable we should have gone to the repository
        Assertions.assertEquals(1, repoCount)
    }


    private fun populateShortHashCache(
        cache: Cache<ShortHashCacheKey, SigningKeyInfo>,
        shortKeyId: ShortHash,
        fullKeyId: SecureHash,
    ) {
        cache.put(
            ShortHashCacheKey(tenantId, shortKeyId),
            mock<SigningKeyInfo> {
                on { fullId }.thenReturn(fullKeyId)
                on { id }.thenReturn(shortKeyId)
            }
        )
    }


    @Test
    fun `generateKeyPair throws KeyAlreadyExistsException if the key already exists`() {
        val tenantId = "ID"
        val category = "category"
        val alias = "alias"
        val scheme = mock<KeyScheme>()
        val context = emptyMap<String, String>()
        val key = mock<SigningKeyInfo>()
        val repo = mock<SigningRepository>()
        val hsmAssociationInfo = mock<HSMAssociationInfo> {
            on { masterKeyAlias } doReturn "root1"
        }
        val tenantInfoService = mock<TenantInfoService> {
            on { lookup(any(), any()) } doReturn hsmAssociationInfo
        }
        whenever(repo.findKey(alias)).doReturn(key)

        val service = makeSoftCryptoService(signingRepository = repo, tenantInfoService = tenantInfoService)
        val exception = assertThrows<KeyAlreadyExistsException> {
            service.generateKeyPair(tenantId, category, alias, scheme = scheme, context = context)
        }
        assertThat(exception.message).contains("The key with alias")
        assertThat(exception.message).contains("already exists for tenant")
    }

    @Test
    fun `can rewrap a managed wrapping key`() {
        cryptoRepositoryWrapping.keys["root"] = sampleWrappingKeyInfo
        // service has a mock root wrapping key, so we have to make one with a real wrapping key
        val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val rootWrappingKey2 = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val myCryptoService = makeSoftCryptoService(
            wrappingRepository = cryptoRepositoryWrapping,
            schemeMetadata = schemeMetadata,
            rootWrappingKey = rootWrappingKey,
            rootWrappingKey2 = rootWrappingKey2
        )
        assertThat(cryptoRepositoryWrapping.keys.contains("alpha")).isFalse()
        myCryptoService.createWrappingKey("alpha", false, emptyMap())
        val wrappedWithRoot1 = checkNotNull(cryptoRepositoryWrapping.keys.get("alpha")).keyMaterial
        val clearKey1 = rootWrappingKey.unwrapWrappingKey(wrappedWithRoot1)

        // try rotating to parent key root2
        myCryptoService.rewrapWrappingKey(CryptoTenants.CRYPTO, "alpha", "root2")
        val wrappedWithRoot2 = checkNotNull(cryptoRepositoryWrapping.keys.get("alpha")).keyMaterial
        val clearKey2 = rootWrappingKey2.unwrapWrappingKey(wrappedWithRoot2)
        val exception = assertThrows<AEADBadTagException> {
            rootWrappingKey.unwrapWrappingKey(wrappedWithRoot2)
        }
        assertThat(exception.message).contains("Tag mismatch")
        assertThat(clearKey2).isEqualTo(clearKey1)
        assertThat(wrappedWithRoot1).isNotEqualTo(wrappedWithRoot2)

        // now let's rotate back to parent key root, and the clear material should be the same
        myCryptoService.rewrapWrappingKey(CryptoTenants.CRYPTO, "alpha", "root")
        val wrappedWithRoot1Again = checkNotNull(cryptoRepositoryWrapping.keys.get("alpha")).keyMaterial
        val clearKey3 = rootWrappingKey.unwrapWrappingKey(wrappedWithRoot1Again)
        assertThat(clearKey3).isEqualTo(clearKey1)

        // AES may have a different initialisation vector so wrappedWithRoot1Again will usually not equal
        // wrappedWithRoot1
    }
}