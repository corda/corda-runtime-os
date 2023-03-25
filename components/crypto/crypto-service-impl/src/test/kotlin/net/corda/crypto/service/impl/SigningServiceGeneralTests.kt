package net.corda.crypto.service.impl

import com.github.benmanes.caffeine.cache.Cache
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.KeyOrderBy
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SigningServiceGeneralTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    @Test
    fun `Should throw original exception failing signing`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cryptoServiceFactory = mock(),
            signingRepositoryFactory = { repo },
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock()
        )
        val thrown = assertThrows(exception::class.java) {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = SignatureSpec("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(repo, times(1)).findKey(any<PublicKey>())
    }

    // @Test
//    fun `Should throw IllegalArgumentException when key is not found for signing`() {
//        val repo = mock<SigningRepository> {
//            on { findKey(any<PublicKey>()) } doReturn null
//        }
//        val signingService = SigningServiceImpl(
//            cryptoServiceFactory = mock(),
//            signingRepositoryFactory = { repo },
//            schemeMetadata = schemeMetadata,
//            digestService = mock(),
//            cache = mock()
//        )
//        assertThrows(IllegalArgumentException::class.java) {
//            signingService.sign(
//                tenantId = UUID.randomUUID().toString(),
//                publicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                signatureSpec = SignatureSpec("NONE"),
//                data = ByteArray(2),
//                context = emptyMap()
//            )
//        }
//    }

    @Test
    fun `Should throw original exception failing derivation`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { repo },
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock(),
        )
        val thrown = assertThrows(exception::class.java) {
            signingService.deriveSharedSecret(
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
        assertSame(exception, thrown)
        verify(repo, times(1)).findKey(any<PublicKey>())
    }

//    @Test
//    fun `Should throw IllegalArgumentException when key is not found for deriving`() {
//        val repo = mock<SigningRepository> {
//            on { findKey(any<PublicKey>()) } doReturn null
//        }
//        val signingService = SigningServiceImpl(
//            signingRepositoryFactory = { repo },
//            cryptoServiceFactory = mock(),
//            schemeMetadata = schemeMetadata,
//            digestService = mock(),
//            cache = mock()
//        )
//        assertThrows(IllegalArgumentException::class.java) {
//            signingService.deriveSharedSecret(
//                tenantId = UUID.randomUUID().toString(),
//                publicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                otherPublicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                context = emptyMap()
//            )
//        }
//    }

    @Test
    fun `Should throw KeyAlreadyExistsException when generating key with existing alias`() {
        val existingKey = SigningKeyInfo(
            id = ShortHash.of("0123456789AB"),
            fullId = parseSecureHash("SHA-256:0123456789ABCDEF"),
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            alias = "alias1",
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            masterKeyAlias = UUID.randomUUID().toString(),
            externalId = null,
            schemeCodeName = ECDSA_SECP256R1_CODE_NAME,
            encodingVersion = 1,
            hsmId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val repo = mock<SigningRepository> {
            on { findKey(anyString()) } doReturn existingKey
        }
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { repo },
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock(),
        )
        assertThrows(KeyAlreadyExistsException::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThrows(KeyAlreadyExistsException::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
    }

    @Test
    fun `Should throw original when failing key generation with alias`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(anyString()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { repo },
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock(),
        )
        var thrown = assertThrows(exception::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        thrown = assertThrows(exception::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        verify(repo, times(2)).findKey(anyString())
    }

    @Test
    fun `Should pass all parameters to cache for lookup function`() {
        val skip = 17
        val take = 21
        val orderBy: KeyOrderBy = KeyOrderBy.ALIAS
        val tenantId: String = UUID.randomUUID().toString()
        val category: String = CryptoConsts.Categories.TLS
        val schemeCodeName: String = UUID.randomUUID().toString()
        val alias: String = UUID.randomUUID().toString()
        val masterKeyAlias: String = UUID.randomUUID().toString()
        val createdAfter: Instant = Instant.now().plusSeconds(-5)
        val createdBefore: Instant = Instant.now()
        val store = mock<SigningRepository> {
            on { query(any(), any(), any(), any()) } doReturn emptyList()
        }
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { store },
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock(),
        )
        val filter = mapOf(
            CATEGORY_FILTER to category,
            SCHEME_CODE_NAME_FILTER to schemeCodeName,
            ALIAS_FILTER to alias,
            MASTER_KEY_ALIAS_FILTER to masterKeyAlias,
            CREATED_AFTER_FILTER to createdAfter.toString(),
            CREATED_BEFORE_FILTER to createdBefore.toString()
        )
        val result = signingService.querySigningKeys(
            tenantId,
            skip,
            take,
            orderBy,
            filter
        )
        assertThat(result).isNotNull
        assertThat(result.size).isEqualTo(0)
        verify(store, times(1)).query(
            skip,
            take,
            SigningKeyOrderBy.ALIAS,
            filter
        )
    }

    @Test
    fun `Should pass order by to lookup function`() {
        KeyOrderBy.values().forEach { orderBy ->
            val skip = 17
            val take = 21
            val tenantId: String = UUID.randomUUID().toString()
            val repo = mock<SigningRepository> {
                on { query(any(), any(), any(), any()) } doReturn emptyList()
            }
            val signingService = SigningServiceImpl(
                signingRepositoryFactory = { repo },
                cryptoServiceFactory = mock(),
                schemeMetadata = schemeMetadata,
                digestService = mock(),
                cache = mock(),
            )
            val filter = emptyMap<String, String>()
            val result = signingService.querySigningKeys(
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

    @Test
    @Suppress("ComplexMethod")
    fun `Should save generated key with alias`() {
        val generatedKey = GeneratedPublicKey(
            publicKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            hsmAlias = UUID.randomUUID().toString()
        )
        val tenantId = UUID.randomUUID().toString()
        val expectedAlias = UUID.randomUUID().toString()
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn ShortHash.of("1234567890AB") }
        val repo = mock<SigningRepository> {
            on { savePublicKey(any()) } doReturn signingKeyInfo
        }
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            hsmId = UUID.randomUUID().toString(),
            instance = mock {
                on { generateKeyPair(any(), any()) } doReturn generatedKey
            }
        )
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { repo },
            cryptoServiceFactory = mock {
                on { findInstance(tenantId, CryptoConsts.Categories.LEDGER) } doReturn ref
            },
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock(),
        )
        var result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            scheme = scheme,
            alias = expectedAlias
        )
        assertThat(generatedKey.publicKey).isEqualTo(result)
        val expectedExternalId = UUID.randomUUID().toString()
        result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            externalId = expectedExternalId,
            scheme = scheme,
            alias = expectedAlias
        )
        assertThat(generatedKey.publicKey).isEqualTo(result)
        verify(repo, times(1)).savePublicKey(
            argThat {
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == null &&
                        keyScheme == scheme &&
                        hsmId == ref.hsmId &&
                        category == ref.category
            }
        )
        verify(repo, times(1)).savePublicKey(
            argThat {
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == expectedExternalId &&
                        keyScheme == scheme &&
                        hsmId == ref.hsmId &&
                        category == ref.category
            }
        )
    }

    @Test
    fun `repository can correctly looks up a signing key by short ids`() {
        val hashA = ShortHash.of("0123456789AB")
        val hashB = ShortHash.of("123456789ABC")
        val keys = listOf(hashA, hashB)
        val mockCachedKey = mock<SigningKeyInfo> { on { id } doReturn hashA }
        val queryCap = argumentCaptor<Iterable<CacheKey>>()
        val cache = mock<Cache<CacheKey, SigningKeyInfo>> {
            on { getAllPresent(queryCap.capture()) } doReturn mapOf(
                CacheKey("tenant", hashA) to mockCachedKey
            )
        }
        val keyIdsCap = argumentCaptor<Set<ShortHash>>()
        val cryptoServiceFactory = mock<CryptoServiceFactory> { }
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn mock() }
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyShortHashes(keyIdsCap.capture()) } doReturn setOf(signingKeyInfo)
        }

        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { repo },
            cryptoServiceFactory = cryptoServiceFactory,
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = cache,
        )

        signingService.lookupSigningKeysByPublicKeyShortHash("tenant", keys)

        val cacheKeys = setOf(CacheKey("tenant", hashA), CacheKey("tenant", hashB))
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
        val queryCap = argumentCaptor<Iterable<CacheKey>>()
        val mockCachedKey = mock<SigningKeyInfo> { on { fullId } doReturn hashA }
        val cache = mock<Cache<CacheKey, SigningKeyInfo>> {
            on { getAllPresent(queryCap.capture()) } doReturn mapOf(
                CacheKey("tenant", shortA) to mockCachedKey
            )
        }
        val cryptoServiceFactory = mock<CryptoServiceFactory> { }
        val fullIdsCap = argumentCaptor<Set<SecureHash>>()
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn mock() }
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyHashes(fullIdsCap.capture()) } doReturn setOf(signingKeyInfo)
        }

        val signingService = SigningServiceImpl(
            signingRepositoryFactory = { repo },
            cryptoServiceFactory = cryptoServiceFactory,
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = cache,
        )

        signingService.lookupSigningKeysByPublicKeyHashes("tenant", keys)

        val cacheKeys = setOf(CacheKey("tenant", shortA), CacheKey("tenant", shortB))
        queryCap.allValues.single().forEach {
            assertThat(it in cacheKeys)
        }
        assertThat(fullIdsCap.allValues.single()).isEqualTo(setOf(hashB))
    }
}