package net.corda.crypto.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SignatureSpecImpl
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
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.core.KeyOrderBy
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.testkit.SecureHashUtils
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

// TODO - move content into SigningServiceImplTest
class SigningServiceGeneralTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        private val wrappingKeyAlias = "Enoch Root"
        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }

        @JvmStatic
        fun keyOrders() = KeyOrderBy.values()

        val tenantId = UUID.randomUUID().toString()

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

        val association =  mock<HSMAssociationInfo> {
            on { masterKeyAlias }.thenReturn(wrappingKeyAlias)
        }
        val mockHsmStore = mock<HSMStore> {
            on { findTenantAssociation(any(), any()) } doReturn association
        }
    }


    @Test
    fun `Should throw original exception failing signing`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val signingService = makeSigningServiceImpl(repo)
        val thrown = assertThrows(exception::class.java) {
            signingService.sign(
                tenantId = tenantId,
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = SignatureSpecImpl("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(repo, times(1)).findKey(any<PublicKey>())
    }

    @Test
    fun `Should throw IllegalArgumentException when key is not found for signing`() {
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doReturn null
        }
        val signingService = makeSigningServiceImpl(repo)
        val publicKey = mock<PublicKey> {
            on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
        }
        assertThrows(IllegalArgumentException::class.java) {
            signingService.sign(
                tenantId = tenantId,
                publicKey = publicKey,
                signatureSpec = SignatureSpecImpl("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
    }

    private fun mockDigestService() = mock<PlatformDigestService> {
        on { hash(any<ByteArray>(), any()) } doReturn SecureHashUtils.randomSecureHash()
    }

    private fun makeCache(): Cache<CacheKey, SigningKeyInfo> =
        Caffeine.newBuilder()
            .expireAfterAccess(3600, TimeUnit.MINUTES)
            .maximumSize(3).build()

    @Test
    fun `Should throw original exception failing derivation`() {
        val exception = RuntimeException("")
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val signingService = makeSigningServiceImpl(repo)
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

    @Test
    fun `Should throw IllegalArgumentException when key is not found for deriving`() {
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doReturn null
        }
        val signingService = makeSigningServiceImpl(repo)
        assertThrows(IllegalArgumentException::class.java) {
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
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            wrappingKeyAlias = wrappingKeyAlias,
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
        val signingService = makeSigningServiceImpl(repo)
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
        val signingService = makeSigningServiceImpl(repo, mock())
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
        val category: String = CryptoConsts.Categories.TLS
        val schemeCodeName: String = UUID.randomUUID().toString()
        val alias: String = UUID.randomUUID().toString()
        val masterKeyAlias: String = UUID.randomUUID().toString()
        val createdAfter: Instant = Instant.now().plusSeconds(-5)
        val createdBefore: Instant = Instant.now()
        val store = mock<SigningRepository> {
            on { query(any(), any(), any(), any()) } doReturn emptyList()
        }
        val signingService = makeSigningServiceImpl(store)
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


    @ParameterizedTest
    @MethodSource("keyOrders")
    fun `Should pass order by to lookup function`(orderBy: KeyOrderBy) {
        val skip = 17
        val take = 21
        val tenantId: String = UUID.randomUUID().toString()
        val repo = mock<SigningRepository> {
            on { query(any(), any(), any(), any()) } doReturn emptyList()
        }
        val signingService = makeSigningServiceImpl(repo)
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

    private fun makeSigningServiceImpl(
        repo: SigningRepository,
        cache: Cache<CacheKey, SigningKeyInfo>? = null,
        cryptoService: CryptoService = mock(),
    ): SigningServiceImpl = SigningServiceImpl(
        cryptoService = cryptoService,
        signingRepositoryFactory = { repo },
        schemeMetadata = schemeMetadata,
        digestService = mockDigestService(),
        signingKeyInfoCache = cache ?: makeCache(),
        hsmStore = mockHsmStore,
    )

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
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn mock() }
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyShortHashes(keyIdsCap.capture()) } doReturn setOf(signingKeyInfo)
        }

        val signingService = makeSigningServiceImpl(repo, cache)
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
        val fullIdsCap = argumentCaptor<Set<SecureHash>>()
        val signingKeyInfo = mock<SigningKeyInfo> { on { id } doReturn mock() }
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyHashes(fullIdsCap.capture()) } doReturn setOf(signingKeyInfo)
        }

        val signingService = makeSigningServiceImpl(repo, cache)
        signingService.lookupSigningKeysByPublicKeyHashes("tenant", keys)

        val cacheKeys = setOf(CacheKey("tenant", shortA), CacheKey("tenant", shortB))
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
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = {
                repoCount++
                repo
            },
            cryptoService = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mockDigestService(),
            signingKeyInfoCache = cache,
            hsmStore = mockHsmStore,
        )
        if (keysInCache >= 1) populateCache(cache, shortKeyId0, fullKeyId0)
        if (keysInCache >= 2) populateCache(cache, shortKeyId1, fullKeyId1)
        fun doLookup() = if (longHashes)
            signingService.lookupSigningKeysByPublicKeyHashes(tenantId, listOf(fullKeyId0, fullKeyId1))
        else
            signingService.lookupSigningKeysByPublicKeyShortHash(tenantId, listOf(shortKeyId0, shortKeyId1))

        val r = doLookup()
        assertEquals(
            setOf(fullKeyId0, fullKeyId1).map { it.toString() }.toSet(),
            r.map { it.fullId.toString() }.toSet()
        )
        assertThat(repoCount).isEqualTo(if (keysInCache == 2) 0 else 1)
        if (longHashes) {
            if (keysInCache == 0) assertEquals(setOf(fullKeyId0, fullKeyId1), hashCaptor.firstValue)
            if (keysInCache == 1) assertEquals(setOf(fullKeyId1), hashCaptor.firstValue)
        } else {
            if (keysInCache == 0) assertEquals(setOf(shortKeyId0, shortKeyId1), shortHashCaptor.firstValue)
            if (keysInCache == 1) assertEquals(setOf(shortKeyId1), shortHashCaptor.firstValue)
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
        val cache = makeCache()
        var repoCount = 0
        val repo = mock<SigningRepository> {
            on { lookupByPublicKeyHashes(hashCaptor.capture()) }.thenReturn(mockDbResults)
        }
        populateCache(cache, shortKeyId0, fullKeyId0)
        val signingService = SigningServiceImpl(
            signingRepositoryFactory = {
                repoCount++
                repo
            },
            cryptoService = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mockDigestService(),
            signingKeyInfoCache = cache,
            hsmStore = mockHsmStore,
        )
        val lookedUpByFullKeyIdsKeys =
            signingService.lookupSigningKeysByPublicKeyHashes(tenantId, listOf(requestedFullKeyId))
        assertEquals(0, lookedUpByFullKeyIdsKeys.size)

        // since we could not find anything in the cache which is 
        // suitable we should have gone to the repository
        assertEquals(1, repoCount)
    }


    private fun populateCache(
        cache: Cache<CacheKey, SigningKeyInfo>,
        shortKeyId: ShortHash,
        fullKeyId: SecureHash,
    ) {
        cache.put(
            CacheKey(tenantId, shortKeyId),
            mock<SigningKeyInfo> {
                on { fullId }.thenReturn(fullKeyId)
                on { id }.thenReturn(shortKeyId)
            }
        )


    }

}