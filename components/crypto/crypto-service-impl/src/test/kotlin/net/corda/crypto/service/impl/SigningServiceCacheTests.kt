package net.corda.crypto.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.parseSecureHash
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.service.SigningService
import net.corda.crypto.softhsm.SigningRepository
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningServiceCacheTests {
    val tenantId = "123"

    val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    lateinit var signingRepositoryFactory: SigningRepositoryFactory

//    lateinit var signingKeysRepository: SigningKeysRepository
    lateinit var signingService: SigningService
    val digestService = PlatformDigestServiceImpl(cipherSchemeMetadata)

    @BeforeEach
    fun setUp() {
        signingRepositoryFactory = mock()
    }

    fun setUpSigningService(cache: Cache<CacheKey, SigningKeyInfo>) {
        signingService =
            SigningServiceImpl(
                mock(),
                signingRepositoryFactory,
                cipherSchemeMetadata,
                digestService,
                cache
            )
    }

    @Test
    fun `lookupByKeyIds returns requested keys from cache if all requested keys are in cache`() {
        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cache =
            createCache()
                .also { cache ->
                    val cachedKey0 = mock<SigningKeyInfo>().also { whenever(it.fullId).thenReturn(fullKeyId0) }
                    val cachedKey1 = mock<SigningKeyInfo>().also { whenever(it.fullId).thenReturn(fullKeyId1) }
                    cache.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                    cache.put(CacheKey(tenantId, shortKeyId1), cachedKey1)
                }
        setUpSigningService(cache)
        assertEquals(
            setOf(fullKeyId0, fullKeyId1),
            signingService.lookupSigningKeysByPublicKeyShortHash(tenantId, setOf(shortKeyId0, shortKeyId1))
                .map { it.fullId }.toSet()
        )
        // verify it didn't go to the database
        verify(signingRepositoryFactory, times(0)).getInstance(any())
    }

    @Test
    fun `lookupByKeyIds returns requested keys from cache and from database if they are not cached`() {
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cache =
            createCache()
                .also { cache ->
                    val cachedKey0 = mock<SigningKeyInfo>().also {
                        whenever(it.id).thenReturn(shortKeyId0)
                        whenever(it.fullId).thenReturn(fullKeyId0)
                    }
                    cache.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                }

        val keysCaptor = argumentCaptor<Set<ShortHash>>()
        signingRepositoryFactory.run {
            val dbFetchedKey = mock<SigningKeyInfo>().also { whenever(it.id).thenReturn(shortKeyId1) }
            val signingRepository = mock<SigningRepository>().also {
                whenever(it.lookupByPublicKeyShortHashes(keysCaptor.capture())).thenReturn(listOf(dbFetchedKey))
            }
            whenever(this.getInstance(eq(tenantId))).thenReturn(signingRepository)
        }

        setUpSigningService(cache)
        val lookedUpByKeyIdsKeys =
            signingService.lookupSigningKeysByPublicKeyShortHash(tenantId, setOf(shortKeyId0, shortKeyId1))

        val expectedNotFoundInCache = setOf(shortKeyId1)
        assertEquals(expectedNotFoundInCache, keysCaptor.firstValue)
        assertEquals(setOf(shortKeyId0, shortKeyId1), lookedUpByKeyIdsKeys.mapTo(mutableSetOf()) { it.id })
        verify(signingRepositoryFactory, times(1)).getInstance(any())
    }

    @Test
    fun `lookupByFullKeyIds returns requested keys from cache if all requested keys are in cache`() {
        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cache =
            createCache()
                .also { cache ->
                    val cachedKey0 = mock<SigningKeyInfo>().also { whenever(it.fullId).thenReturn(fullKeyId0) }
                    val cachedKey1 = mock<SigningKeyInfo>().also { whenever(it.fullId).thenReturn(fullKeyId1) }
                    cache.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                    cache.put(CacheKey(tenantId, shortKeyId1), cachedKey1)
                }

        setUpSigningService(cache)
        assertEquals(
            setOf(fullKeyId0, fullKeyId1),
            signingService.lookupSigningKeysByPublicKeyHashes(tenantId, setOf(fullKeyId0, fullKeyId1)).map { it.fullId }.toSet()
        )
        // verify it didn't go to the database
        verify(signingRepositoryFactory, times(0)).getInstance(any())
    }

    @Test
    fun `lookupByFullKeyIds returns requested keys from cache and from database if they are not cached`() {
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cache =
            createCache()
                .also { cache ->
                    val cachedKey0 = mock<SigningKeyInfo>().also {
                        whenever(it.id).thenReturn(shortKeyId0)
                        whenever(it.fullId).thenReturn(fullKeyId0)
                    }
                    cache.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                }

        val keysCaptor = argumentCaptor<Set<SecureHash>>()
        signingRepositoryFactory.run {
            val dbFetchedKey = mock<SigningKeyInfo>().also {
                whenever(it.id).thenReturn(shortKeyId1)
                whenever(it.fullId).thenReturn(fullKeyId1)
            }
            val signingRepository = mock<SigningRepository>().also {
                whenever(it.lookupByPublicKeyHashes(keysCaptor.capture())).thenReturn(listOf(dbFetchedKey))
            }
            whenever(this.getInstance(eq(tenantId))).thenReturn(signingRepository)
        }

        setUpSigningService(cache)
        val lookedUpByFullKeyIdsKeys =
            signingService.lookupSigningKeysByPublicKeyHashes(tenantId, setOf(fullKeyId0, fullKeyId1))

        val expectedNotFoundInCache = setOf(fullKeyId1)
        assertEquals(expectedNotFoundInCache, keysCaptor.firstValue)
        assertEquals(setOf(fullKeyId0, fullKeyId1), lookedUpByFullKeyIdsKeys.mapTo(mutableSetOf()) { it.fullId })
        verify(signingRepositoryFactory, times(1)).getInstance(any())
    }

    @Test
    fun `lookupByFullKeyIds will not return clashed keys on short key id`() {
        val fullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)
        val requestedFullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111112")

        val cache =
            createCache()
                .also { cache ->
                    val cachedKey = mock<SigningKeyInfo>().also {
                        whenever(it.id).thenReturn(shortKeyId)
                        whenever(it.fullId).thenReturn(fullKeyId)
                    }
                    cache.put(CacheKey(tenantId, shortKeyId), cachedKey)
                }

        val keysCaptor = argumentCaptor<Set<SecureHash>>()
        signingRepositoryFactory.run {
            val signingRepository = mock<SigningRepository>().also {
                whenever(it.lookupByPublicKeyHashes(keysCaptor.capture())).thenReturn(setOf())
            }
            whenever(this.getInstance(eq(tenantId))).thenReturn(signingRepository)
        }

        setUpSigningService(cache)
        val lookedUpByFullKeyIdsKeys =
            signingService.lookupSigningKeysByPublicKeyHashes(tenantId, setOf(requestedFullKeyId))

        // TODO This currently goes to look for clashed on short key id keys up in DB, it should be changed so that id doesn't as
        //  we can't have clashed short key ids per tenant
        val keysLookedUpInDb = setOf(requestedFullKeyId)
        assertEquals(keysLookedUpInDb, keysCaptor.firstValue)
        assertEquals(setOf(), lookedUpByFullKeyIdsKeys.mapTo(mutableSetOf()) { it.fullId })
        verify(signingRepositoryFactory, times(1)).getInstance(any())
    }

    @Test
    fun `lookupByFullKeyId returns requested key from cache if cached`() {
        val fullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)

        val cache =
            createCache().also { cache ->
                val cachedKey = mock<SigningKeyInfo>().also {
                    whenever(it.id).thenReturn(shortKeyId)
                    whenever(it.fullId).thenReturn(fullKeyId)
                }
                cache.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }

        setUpSigningService(cache)
        val lookedUpByFullKeyIdKey = (signingService as SigningServiceImpl).lookupByFullKeyId(tenantId, fullKeyId)

        assertEquals(fullKeyId, lookedUpByFullKeyIdKey!!.fullId)
        verify(signingRepositoryFactory, times(0)).getInstance(any())
    }

    @Test
    fun `lookupByFullKeyId will not return clashed keys on short key id`() {
        val fullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)
        val requestedFullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111112")

        val cache =
            createCache()
                .also { cache ->
                    val cachedKey = mock<SigningKeyInfo>().also {
                        whenever(it.id).thenReturn(shortKeyId)
                        whenever(it.fullId).thenReturn(fullKeyId)
                    }
                    cache.put(CacheKey(tenantId, shortKeyId), cachedKey)
                }

        setUpSigningService(cache)
        val lookedUpByFullKeyIdKey = (signingService as SigningServiceImpl).lookupByFullKeyId(tenantId, requestedFullKeyId)

        assertNull(lookedUpByFullKeyIdKey)
        verify(signingRepositoryFactory, times(0)).getInstance(any())
    }

    @Test
    fun `composite key look up hits the cache for key leaves first then the database`() {
        val keyEncoded1 = byteArrayOf(1, 2, 3)
        val keyEncoded2 = byteArrayOf(4, 5, 6)
        val leafKey1 = mock<PublicKey>().also {
            whenever(it.encoded).thenReturn(keyEncoded1)
        }
        val leafKey2 = mock<PublicKey>().also {
            whenever(it.encoded).thenReturn(keyEncoded2)
        }

        val compositeSigningKey =
            mock<CompositeKey>()
                .also {
                    whenever(it.leafKeys).thenReturn(setOf(leafKey1, leafKey2))
                }

        val fullKeyId1 = leafKey1.fullIdHash()
        val shortKeyId1 = ShortHash.of(fullKeyId1)
        val fullKeyId2 = leafKey2.fullIdHash()
        val shortKeyId2 = ShortHash.of(fullKeyId2)

        val cacheLookupCaptor = argumentCaptor<Set<CacheKey>>()
        val cache =
            mock<Cache<CacheKey, SigningKeyInfo>>()
                .also { cache ->
                    whenever(cache.getAllPresent(cacheLookupCaptor.capture()))
                        .thenReturn(
                            mapOf()
                        )
                }

        val databaseLookUpCaptor = argumentCaptor<Set<SecureHash>>()
        signingRepositoryFactory.run {
            val signingRepository = mock<SigningRepository>().also {
                val cachedKey1 = mock<SigningKeyInfo>().also {
                    whenever(it.id).thenReturn(shortKeyId1)
                    whenever(it.fullId).thenReturn(fullKeyId1)
                }
                val cachedKey2 = mock<SigningKeyInfo>().also {
                    whenever(it.id).thenReturn(shortKeyId2)
                    whenever(it.fullId).thenReturn(fullKeyId2)
                }
                whenever(it.lookupByPublicKeyHashes(databaseLookUpCaptor.capture())).thenReturn(listOf(cachedKey1, cachedKey2))
            }
            whenever(this.getInstance(eq(tenantId))).thenReturn(signingRepository)
        }

        setUpSigningService(cache)
        val ownedKeyRecord = (signingService as SigningServiceImpl).getOwnedKeyRecord(tenantId, compositeSigningKey)
        assertNotNull(ownedKeyRecord)
        // should be returning the first key leaf only
        assertEquals(leafKey1, ownedKeyRecord.publicKey)
        // assert we hit the cache for both the leaves
        assertEquals(setOf(CacheKey(tenantId, shortKeyId1), CacheKey(tenantId, shortKeyId2)), cacheLookupCaptor.firstValue)
        // assert we also hit the database because leaves were missing on cache
        assertEquals(setOf(fullKeyId1, fullKeyId2), databaseLookUpCaptor.firstValue)
        verify(signingRepositoryFactory, times(1)).getInstance(any())
    }

    private fun createCache(): Cache<CacheKey, SigningKeyInfo> =
        CacheFactoryImpl().build(
            "Signing-Key-Cache",
            Caffeine.newBuilder()
                .expireAfterAccess(10000, TimeUnit.MINUTES)
                .maximumSize(60)
        )
}