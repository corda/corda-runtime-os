package net.corda.crypto.persistence.impl.tests

/**  TODO Port this to new code
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

    lateinit var signingKeysRepository: SigningKeysRepository
    lateinit var signingKeyStore: SigningKeyStoreImpl.Impl

    fun setUpSigningKeyStore() {
        signingKeyStore =
            SigningKeyStoreImpl.Impl(
                mock(),
            )
    }

    @BeforeEach
    fun setUp() {
        signingKeysRepository = mock()
    }

    @Test
    fun `lookupByKeyIds returns requested keys from cache if all requested keys are in cache`() {
        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId0) }
            val cachedKey1 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId1) }
            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                it.put(CacheKey(tenantId, shortKeyId1), cachedKey1)
            }
        }
        setUpSigningKeyStore()
        assertEquals(
            setOf(fullKeyId0, fullKeyId1),
            signingKeyStore.lookupByKeyIds(tenantId, setOf(shortKeyId0, shortKeyId1)).map { it.fullId }.toSet()
        )
        // verify it didn't go to the database
        verify(signingKeysRepository, times(0)).findKeysByIds(any(), anyString(), any())
    }

    @Test
    fun `lookupByKeyIds returns requested keys from cache and from database if they are not cached`() {
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId0)
                whenever(it.fullId).thenReturn(fullKeyId0)
            }
            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
            }
        }

        val keysCaptor = argumentCaptor<Set<ShortHash>>()
        signingKeysRepository.run {
            val dbFetchedKey = mock<SigningCachedKey>().also { whenever(it.id).thenReturn(shortKeyId1) }
            whenever(this.findKeysByIds(any(), eq(tenantId), keysCaptor.capture())).thenReturn(setOf(dbFetchedKey))
        }

        setUpSigningKeyStore()
        val lookedUpByKeyIdsKeys = signingKeyStore.lookupByKeyIds(tenantId, setOf(shortKeyId0, shortKeyId1))

        val expectedNotFoundInCache = setOf(shortKeyId1)
        assertEquals(expectedNotFoundInCache, keysCaptor.firstValue)
        assertEquals(setOf(shortKeyId0, shortKeyId1), lookedUpByKeyIdsKeys.mapTo(mutableSetOf()) { it.id })
        verify(signingKeysRepository, times(1)).findKeysByIds(any(), anyString(), any())
    }

    @Test
    fun `lookupByFullKeyIds returns requested keys from cache if all requested keys are in cache`() {
        // Remember key ids cannot clash for same tenant so short keys of testing keys need to be different
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId0) }
            val cachedKey1 = mock<SigningCachedKey>().also { whenever(it.fullId).thenReturn(fullKeyId1) }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
                it.put(CacheKey(tenantId, shortKeyId1), cachedKey1)
            }
        }

        setUpSigningKeyStore()
        assertEquals(
            setOf(fullKeyId0, fullKeyId1),
            signingKeyStore.lookupByFullKeyIds(tenantId, setOf(fullKeyId0, fullKeyId1)).map { it.fullId }.toSet()
        )
        // verify it didn't go to the database
        verify(signingKeysRepository, times(0)).findKeysByFullIds(any(), anyString(), any())
    }

    @Test
    fun `lookupByFullKeyIds returns requested keys from cache and from database if they are not cached`() {
        val fullKeyId0 = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId0 = ShortHash.of(fullKeyId0)
        val fullKeyId1 = parseSecureHash("SHA-256:BBC12345678911111111111111")
        val shortKeyId1 = ShortHash.of(fullKeyId1)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey0 = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId0)
                whenever(it.fullId).thenReturn(fullKeyId0)
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId0), cachedKey0)
            }
        }

        val keysCaptor = argumentCaptor<Set<SecureHash>>()
        signingKeysRepository.run {
            val dbFetchedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId1)
                whenever(it.fullId).thenReturn(fullKeyId1)
            }
            whenever(this.findKeysByFullIds(any(), eq(tenantId), keysCaptor.capture())).thenReturn(setOf(dbFetchedKey))
        }

        setUpSigningKeyStore()
        val lookedUpByFullKeyIdsKeys = signingKeyStore.lookupByFullKeyIds(tenantId, setOf(fullKeyId0, fullKeyId1))

        val expectedNotFoundInCache = setOf(fullKeyId1)
        assertEquals(expectedNotFoundInCache, keysCaptor.firstValue)
        assertEquals(setOf(fullKeyId0, fullKeyId1), lookedUpByFullKeyIdsKeys.mapTo(mutableSetOf()) { it.fullId })
        verify(signingKeysRepository, times(1)).findKeysByFullIds(any(), anyString(), any())
    }

    @Test
    fun `lookupByFullKeyIds will not return clashed keys on short key id`() {
        val fullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)
        val requestedFullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111112")

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId)
                whenever(it.fullId).thenReturn(fullKeyId)
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }
        }

        val keysCaptor = argumentCaptor<Set<SecureHash>>()
        signingKeysRepository.run {
            whenever(this.findKeysByFullIds(any(), eq(tenantId), keysCaptor.capture())).thenReturn(setOf())
        }

        setUpSigningKeyStore()
        val lookedUpByFullKeyIdsKeys = signingKeyStore.lookupByFullKeyIds(tenantId, setOf(requestedFullKeyId))

        // TODO This currently goes to look for clashed on short key id keys up in DB, it should be changed so that id doesn't as
        //  we can't have clashed short key ids per tenant
        val keysLookedUpInDb = setOf(requestedFullKeyId)
        assertEquals(keysLookedUpInDb, keysCaptor.firstValue)
        assertEquals(setOf(), lookedUpByFullKeyIdsKeys.mapTo(mutableSetOf()) { it.fullId })
        verify(signingKeysRepository, times(1)).findKeysByFullIds(any(), anyString(), any())
    }

    @Test
    fun `lookupByFullKeyId returns requested key from cache if cached`() {
        val fullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId)
                whenever(it.fullId).thenReturn(fullKeyId)
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }
        }

        setUpSigningKeyStore()
        val lookedUpByFullKeyIdKey = signingKeyStore.lookupByFullKeyId(tenantId, fullKeyId)

        assertEquals(fullKeyId, lookedUpByFullKeyIdKey!!.fullId)
        verify(signingKeysRepository, times(0)).findKeysByFullIds(any(), anyString(), any())
    }


    @Test
    fun `lookupByFullKeyId will not return clashed keys on short key id`() {
        val fullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111111")
        val shortKeyId = ShortHash.of(fullKeyId)
        val requestedFullKeyId = parseSecureHash("SHA-256:ABC12345678911111111111112")

        val cacheFactory: (CryptoSigningServiceConfig) -> Cache<CacheKey, SigningCachedKey> = {
            val cachedKey = mock<SigningCachedKey>().also {
                whenever(it.id).thenReturn(shortKeyId)
                whenever(it.fullId).thenReturn(fullKeyId)
            }

            createCache(config = signingServiceConfig).also {
                it.put(CacheKey(tenantId, shortKeyId), cachedKey)
            }
        }

        setUpSigningKeyStore()
        val lookedUpByFullKeyIdKey = signingKeyStore.lookupByFullKeyId(tenantId, requestedFullKeyId)

        assertNull(lookedUpByFullKeyIdKey)
        verify(signingKeysRepository, times(0)).findKeysByFullIds(any(), anyString(), any())
    }
}
        */