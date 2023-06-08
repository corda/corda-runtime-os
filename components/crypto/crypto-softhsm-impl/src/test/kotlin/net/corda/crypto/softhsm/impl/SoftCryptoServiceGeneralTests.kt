package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.impl.infra.TestWrappingRepository
import net.corda.crypto.softhsm.impl.infra.makeSoftCryptoService
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.X25519_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.UUID
import javax.persistence.QueryTimeoutException
import kotlin.test.assertTrue

/* SoftCryptoService tests that do not require wrapping keys */
class SoftCryptoServiceGeneralTests {
    private val schemeMetadata = CipherSchemeMetadataImpl()
    private val UNSUPPORTED_SIGNATURE_SCHEME = CipherSchemeMetadataProvider().COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
    private val cryptoRepositoryWrapping = TestWrappingRepository()
    private val sampleWrappingKeyInfo = WrappingKeyInfo(1, "n", byteArrayOf(), 1, "wrappingKey")
    val defaultContext =
        mapOf(CRYPTO_TENANT_ID to UUID.randomUUID().toString(), CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER)
    private val service = makeSoftCryptoService(
        wrappingRepository = cryptoRepositoryWrapping,
        schemeMetadata = schemeMetadata,
        rootWrappingKey = mock(),
    )
    private val tenantId = UUID.randomUUID().toString()

    companion object {
        @JvmStatic
        fun keyOrders() = KeyOrderBy.values()
    }

    @Test
    fun `Should throw IllegalStateException when wrapping key alias exists and failIfExists is true`() {
        val alias = "stuff"
        cryptoRepositoryWrapping.keys[alias] = sampleWrappingKeyInfo
        assertThrows<IllegalStateException> {
            service.createWrappingKey(alias, true, emptyMap())
        }
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
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    alias = "whatever",
                    wrappingKeyAlias = "stuff3",
                ),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing empty data array`() {
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = service.supportedSchemes.keys.first { it.codeName == ECDSA_SECP256R1_CODE_NAME },
                    signatureSpec = SignatureSpecs.ECDSA_SHA256
                ),
                ByteArray(0),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using unsupported scheme`() {
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    signatureSpec = SignatureSpecs.ECDSA_SHA256
                ),
                ByteArray(0),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using scheme which does not support signing`() {
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        wrappingKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = service.supportedSchemes.keys.first { it.codeName == X25519_CODE_NAME },
                    signatureSpec = SignatureSpecs.EDDSA_ED25519
                ),
                ByteArray(0),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key and spec is not SharedSecretWrappedSpec`() {
        assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(mock(), defaultContext)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using scheme which does not support it`() {
        val keyScheme = service.supportedSchemes.keys.first { it.codeName == EDDSA_ED25519_CODE_NAME }
        val myKeyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        assertThrows<IllegalArgumentException> {
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
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using keys with different schemes`() {
        val keyScheme = service.supportedSchemes.keys.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        val myKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256K1_CODE_NAME)
        assertThrows<IllegalArgumentException> {
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
        assertThrows<CryptoException> {
            cryptoServiceExploding.createWrappingKey("foo", true, emptyMap())
        }
    }

    @Test
    fun `Should throw original exception when a DB query fails with IllegalStateException`() {
        val cryptoServiceExploding = makeSoftCryptoService(wrappingRepository = mock() {
            on { findKey(any()) } doThrow IllegalStateException()
        })
        assertThrows<IllegalStateException> {
            cryptoServiceExploding.createWrappingKey("foo", true, emptyMap())
        }
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


}