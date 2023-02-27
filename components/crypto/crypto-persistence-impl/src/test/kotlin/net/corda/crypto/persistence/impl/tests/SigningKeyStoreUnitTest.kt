package net.corda.crypto.persistence.impl.tests

import com.github.benmanes.caffeine.cache.Cache
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.crypto.config.impl.CryptoSigningServiceConfig
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.impl.SigningKeyStoreImpl
import net.corda.crypto.persistence.impl.SigningKeyStoreImpl.Impl.CacheKey
import net.corda.crypto.persistence.impl.SigningKeyStoreImpl.Impl.Companion.createCache
import net.corda.crypto.persistence.impl.SigningKeysRepository
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNull

// TODO This is to be renamed to SigningKeyStoreTest once `SigningKeyStoreTests` gets ported/ removed
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningKeyStoreUnitTest {

    val signingServiceConfig = ConfigChangedEvent(
        mock(),
        run {
            mapOf(
                ConfigKeys.CRYPTO_CONFIG to
                        mock<SmartConfig>().also {
                            val cacheConfig = mock<SmartConfig>().also {
                                whenever(it.getLong("expireAfterAccessMins")).thenReturn(5L)
                                whenever(it.getLong("maximumSize")).thenReturn(3)
                            }

                            val signingServiceConfig = mock<SmartConfig>().also {
                                whenever(it.getConfig("cache")).thenReturn(cacheConfig)
                            }

                            whenever(it.getConfig("signingService")).thenReturn(signingServiceConfig)
                        }
            )
        }
    ).config.toCryptoConfig().signingService()

    val tenantId = "123"

    val cipherSchemeMetadataImpl = CipherSchemeMetadataImpl()
    lateinit var connectionsFactory: CryptoConnectionsFactory

    lateinit var signingKeysRepository: SigningKeysRepository
    lateinit var signingKeyStore: SigningKeyStoreImpl.Impl

    fun setUpSigningKeyStore(cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey>) {
        signingKeyStore =
            SigningKeyStoreImpl.Impl(
                signingServiceConfig,
                mock(),
                cipherSchemeMetadataImpl,
                connectionsFactory,
                PlatformDigestServiceImpl(cipherSchemeMetadataImpl),
                signingKeysRepository,
                cacheFactory
            )
    }

    @BeforeEach
    fun setUp() {
        signingKeysRepository = mock()
        // resetting this state for verify
        connectionsFactory = mock<CryptoConnectionsFactory>().also {
            val entityManagerFactory = mock<EntityManagerFactory>().also {
                val entityManager = mock<EntityManager>()
                whenever(it.createEntityManager()).thenReturn(entityManager)
            }
            whenever(it.getEntityManagerFactory(tenantId)).thenReturn(entityManagerFactory)
        }
    }

    @Test
    fun `lookupByKeyIds returns requested keys from cache if all requested keys are in cache`() {
        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = SecureHash.parse("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId0.toString()) }
            val cachedKey1 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId1.toString()) }
            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                it.put(CacheKey(tenantId, shortKeyId1), cachedKey1)
            }
        }
        setUpSigningKeyStore(cacheFactory)
        assertEquals(
            setOf(fullKeyId0, fullKeyId1).map { it.toString() }.toSet(),
            signingKeyStore.lookupByKeyIds(tenantId, setOf(shortKeyId0, shortKeyId1)).map { it.fullId }.toSet()
        )
        // verify it didn't go to the database
        verify(connectionsFactory, times(0)).getEntityManagerFactory(any())
    }

    @Test
    fun `lookupByKeyIds returns requested keys from cache and from database if they are not cached`() {
        val fullKeyId0 = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = SecureHash.parse("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId0.toString())
                whenever(it.fullId).thenReturn(fullKeyId0.toString())
            }
            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
            }
        }

        val keysCaptor = argumentCaptor<Set<ShortHash>>()
        signingKeysRepository.run {
            val dbFetchedKey = mock<SigningCachedKey>().also { whenever(it.id).thenReturn(shortKeyId1.value) }
            whenever(this.findKeysByIds(any(), eq(tenantId), keysCaptor.capture())).thenReturn(setOf(dbFetchedKey))
        }

        setUpSigningKeyStore(cacheFactory)
        val lookedUpByKeyIdsKeys = signingKeyStore.lookupByKeyIds(tenantId, setOf(shortKeyId0, shortKeyId1))

        val expectedNotFoundInCache = setOf(shortKeyId1)
        assertEquals(expectedNotFoundInCache, keysCaptor.firstValue)
        assertEquals(setOf(shortKeyId0.value, shortKeyId1.value), lookedUpByKeyIdsKeys.mapTo(mutableSetOf()) { it.id })
        verify(connectionsFactory, times(1)).getEntityManagerFactory(any())
    }

    @Test
    fun `lookupByFullKeyIds returns requested keys from cache if all requested keys are in cache`() {
        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = SecureHash.parse("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId0.toString()) }
            val cachedKey1 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId1.toString()) }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                it.put(CacheKey(tenantId, shortKeyId1), cachedKey1)
            }
        }

        setUpSigningKeyStore(cacheFactory)
        assertEquals(
            setOf(fullKeyId0, fullKeyId1).map { it.toString() }.toSet(),
            signingKeyStore.lookupByFullKeyIds(tenantId, setOf(fullKeyId0, fullKeyId1)).map { it.fullId }.toSet()
        )
        // verify it didn't go to the database
        verify(connectionsFactory, times(0)).getEntityManagerFactory(any())
    }

    @Test
    fun `lookupByFullKeyIds returns requested keys from cache and from database if they are not cached`() {
        val fullKeyId0 = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = SecureHash.parse("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId0.toString())
                whenever(it.fullId).thenReturn(fullKeyId0.toString())
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
            }
        }

        val keysCaptor = argumentCaptor<Set<SecureHash>>()
        signingKeysRepository.run {
            val dbFetchedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId1.value)
                whenever(it.fullId).thenReturn(fullKeyId1.toString())
            }
            whenever(this.findKeysByFullIds(any(), eq(tenantId), keysCaptor.capture())).thenReturn(setOf(dbFetchedKey))
        }

        setUpSigningKeyStore(cacheFactory)
        val lookedUpByFullKeyIdsKeys = signingKeyStore.lookupByFullKeyIds(tenantId, setOf(fullKeyId0, fullKeyId1))

        val expectedNotFoundInCache = setOf(fullKeyId1)
        assertEquals(expectedNotFoundInCache, keysCaptor.firstValue)
        assertEquals(setOf(fullKeyId0.toString(), fullKeyId1.toString()), lookedUpByFullKeyIdsKeys.mapTo(mutableSetOf()) { it.fullId })
        verify(connectionsFactory, times(1)).getEntityManagerFactory(any())
    }

    @Test
    fun `lookupByFullKeyIds will not return clashed keys on short key id`() {
        val fullKeyId = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)
        val requestedFullKeyId = SecureHash.parse("SHA-256:ABC12345678911111111111112")

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId.toString())
                whenever(it.fullId).thenReturn(fullKeyId.toString())
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }
        }

        val keysCaptor = argumentCaptor<Set<SecureHash>>()
        signingKeysRepository.run {
            whenever(this.findKeysByFullIds(any(), eq(tenantId), keysCaptor.capture())).thenReturn(setOf())
        }

        setUpSigningKeyStore(cacheFactory)
        val lookedUpByFullKeyIdsKeys = signingKeyStore.lookupByFullKeyIds(tenantId, setOf(requestedFullKeyId))

        // TODO This currently goes to look for clashed on short key id keys up in DB, it should be changed so that id doesn't as
        //  we can't have clashed short key ids per tenant
        val keysLookedUpInDb = setOf(requestedFullKeyId)
        assertEquals(keysLookedUpInDb, keysCaptor.firstValue)
        assertEquals(setOf(), lookedUpByFullKeyIdsKeys.mapTo(mutableSetOf()) { it.fullId })
        verify(connectionsFactory, times(1)).getEntityManagerFactory(any())
    }

    @Test
    fun `lookupByFullKeyId returns requested key from cache if cached`() {
        val fullKeyId = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId.toString())
                whenever(it.fullId).thenReturn(fullKeyId.toString())
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }
        }

        setUpSigningKeyStore(cacheFactory)
        val lookedUpByFullKeyIdKey = signingKeyStore.lookupByFullKeyId(tenantId, fullKeyId)

        assertEquals(fullKeyId.toString(), lookedUpByFullKeyIdKey!!.fullId)
        verify(connectionsFactory, times(0)).getEntityManagerFactory(any())
    }


    @Test
    fun `lookupByFullKeyId will not return clashed keys on short key id`() {
        val fullKeyId = SecureHash.parse("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)
        val requestedFullKeyId = SecureHash.parse("SHA-256:ABC12345678911111111111112")

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId.toString())
                whenever(it.fullId).thenReturn(fullKeyId.toString())
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }
        }

        setUpSigningKeyStore(cacheFactory)
        val lookedUpByFullKeyIdKey = signingKeyStore.lookupByFullKeyId(tenantId, requestedFullKeyId)

        assertNull(lookedUpByFullKeyIdKey)
        verify(connectionsFactory, times(0)).getEntityManagerFactory(any())
    }
}