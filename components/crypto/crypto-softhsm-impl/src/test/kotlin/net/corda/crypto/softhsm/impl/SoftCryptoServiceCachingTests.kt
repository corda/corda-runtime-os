package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.impl.infra.CountingWrappingKey
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

/**
 * Testing of the crypto service with caching.
 *
 * There is no way of testing the eviction in timely manner or without having special setup for the cache during
 * testing as by default the eviction is scheduled on an executor, so it's not exactly deterministic,.
 */

class SoftCryptoServiceCachingTests {
    val schemeMetadata = CipherSchemeMetadataImpl()

    val wrapCount = AtomicInteger()
    val unwrapCount = AtomicInteger()
    val rootWrappingKey =
        CountingWrappingKey(WrappingKeyImpl.generateWrappingKey(schemeMetadata), wrapCount, unwrapCount)

    fun makePrivateKeyCache(): Cache<PublicKey, PrivateKey> = CacheFactoryImpl().build(
        "test private key cache", Caffeine.newBuilder()
            .expireAfterAccess(3600, TimeUnit.MINUTES)
            .maximumSize(3)
    )

    private fun makeWrappingKeyCache(): Cache<String, WrappingKey> = CacheFactoryImpl().build(
        "test wrapping key cache", Caffeine.newBuilder()
            .expireAfterAccess(3600, TimeUnit.MINUTES)
            .maximumSize(100)
    )

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `private key unwrapping and identity works as expected`(cachePrivateKeys: Boolean) {
        // setup
        val privateKeyCache = if (cachePrivateKeys) makePrivateKeyCache() else null
        val wrappingKeyCache = makeWrappingKeyCache()
        val wrappingKeyAlias = "wrapper1"
        val myCryptoService = makeSoftCryptoService(privateKeyCache, wrappingKeyCache)
        val rsaScheme =
            myCryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first

        // set up a second level wrapping key
        myCryptoService.createWrappingKey(wrappingKeyAlias, true, emptyMap())
        val wrappingKey = wrappingKeyCache.getIfPresent(wrappingKeyAlias)
        assertNotNull(wrappingKey)

        // make two key pairs
        val keyPair1 =
            myCryptoService.generateKeyPair(KeyGenerationSpec(rsaScheme, "key-1", wrappingKeyAlias), emptyMap())
        val keyPair2 =
            myCryptoService.generateKeyPair(KeyGenerationSpec(rsaScheme, "key-2", wrappingKeyAlias), emptyMap())

        // if we have a private key cache, it should now have entries for both key pairs
        val privateKey1FromCache = privateKeyCache?.getIfPresent(keyPair1.publicKey)
        val privateKey2FromCache = privateKeyCache?.getIfPresent(keyPair2.publicKey)

        if (privateKeyCache != null) {
            assertNotNull(privateKey1FromCache)
            assertNotNull(privateKey2FromCache)
        }

        // clear the private key cache, if there is one
        privateKeyCache?.invalidate(keyPair1.publicKey)
        privateKeyCache?.invalidate(keyPair2.publicKey)

        // make spec objects with the public key material 
        val key1Spec = KeyMaterialSpec(keyPair1.keyMaterial, wrappingKeyAlias, keyPair1.encodingVersion)
        val key2Spec = KeyMaterialSpec(keyPair2.keyMaterial, wrappingKeyAlias, keyPair2.encodingVersion)


        val key11 = myCryptoService.getPrivateKey(keyPair1.publicKey, key1Spec)
        val key11c = privateKeyCache?.getIfPresent(keyPair1.publicKey)
        val key1direct = wrappingKey.unwrap(key1Spec.keyMaterial)
        val key2direct = wrappingKey.unwrap(key2Spec.keyMaterial)
        val key21 = myCryptoService.getPrivateKey(keyPair2.publicKey, key2Spec)
        val key21c = privateKeyCache?.getIfPresent(keyPair2.publicKey)
        if (cachePrivateKeys) {
            assertEquals(key1direct, key11c)
            assertEquals(key2direct, key21c)
        }
        val key12 = myCryptoService.getPrivateKey(keyPair1.publicKey, key1Spec)
        val key22 = myCryptoService.getPrivateKey(keyPair2.publicKey, key2Spec)
        assertNotSame(key1direct, key2direct)
        assertNotSame(key12, key22)
        assertNotSame(key1direct, key12)
        assertEquals(key1direct, key12)
        assertEquals(key2direct, key22)
        // the keys we pulled out are reconstructed from encrypted key material, so are
        // not the same objects but are equal
        if (privateKey1FromCache != null) {
            assertNotSame(key1direct, privateKey1FromCache)
            assertEquals(key11, privateKey1FromCache)
            assertEquals(key11, key1direct)
            assertEquals(privateKey1FromCache, key1direct)

        }
        if (privateKey2FromCache != null) {
            assertNotSame(key22, privateKey2FromCache)
            assertEquals(key21, privateKey2FromCache)
        }

        Assertions.assertThat(unwrapCount.get()).isEqualTo(if (cachePrivateKeys) 4 else 6)
    }


    @Test
    fun `wrapPrivateKey should put to cache using public key as cache key`() {
        val myCryptoService = makeSoftCryptoService(makePrivateKeyCache())
        myCryptoService.createWrappingKey("master-alias", true, emptyMap())
        val scheme = myCryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first
        val key = myCryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key-1", "master-alias"), emptyMap())
        val keySpec = KeyMaterialSpec(key.keyMaterial, "master-alias", key.encodingVersion)
        myCryptoService.getPrivateKey(key.publicKey, keySpec)
        assertThat(unwrapCount.get()).isEqualTo(0)
        assertThat(wrapCount.get()).isEqualTo(2)
    }

    private fun makeSoftCryptoService(
        privateKeyCache: Cache<PublicKey, PrivateKey>? = null,
        wrappingKeyCache: Cache<String, WrappingKey>? = null
    ) =
        SoftCryptoService(
            TestWrappingKeyStore(mock()),
            schemeMetadata,
            rootWrappingKey,
            wrappingKeyCache,
            privateKeyCache,
            wrappingKeyFactory = {
                CountingWrappingKey(
                    WrappingKeyImpl.generateWrappingKey(it),
                    wrapCount,
                    unwrapCount
                )
            }
        )

    @Test
    fun `createWrappingKey should put to cache using public key as cache key`() {
        val schemeMetadata = CipherSchemeMetadataImpl()
        val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)

        val alias = UUID.randomUUID().toString()
        val unknownAlias = UUID.randomUUID().toString()
        val cacheAlias = UUID.randomUUID().toString()

        var saveCount = 0
        var findCount = 0
        val countingWrappingStore = object : TestWrappingKeyStore(mock()) {
            override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
                saveCount++
                return super.saveWrappingKey(alias, key)
            }

            override fun findWrappingKey(alias: String): WrappingKeyInfo? {
                findCount++
                return super.findWrappingKey(alias)
            }
        }
        val wrappingKeyCache = makeWrappingKeyCache()
        val myCryptoService = SoftCryptoService(
            countingWrappingStore, schemeMetadata,
            rootWrappingKey, wrappingKeyCache, makePrivateKeyCache()
        )

        // starting fresh, all 3 aliases are missing from both store and cache
        assertNull(wrappingKeyCache.getIfPresent(alias))
        assertNull(wrappingKeyCache.getIfPresent(unknownAlias))
        assertNull(wrappingKeyCache.getIfPresent(cacheAlias))
        assertNull(countingWrappingStore.findWrappingKey(alias))
        assertNull(countingWrappingStore.findWrappingKey(unknownAlias))
        assertNull(countingWrappingStore.findWrappingKey(cacheAlias))

        assertThat(findCount).isEqualTo(3)
        assertThat(saveCount).isEqualTo(0)

        myCryptoService.createWrappingKey(alias, true, mapOf())

        assertThat(findCount).isEqualTo(4) // we do a find to check for conflicts
        assertThat(saveCount).isEqualTo(1)

        // now the cache and store should have alias but not the other two
        assertNotNull(wrappingKeyCache.getIfPresent(alias))
        assertNull(wrappingKeyCache.getIfPresent(unknownAlias))
        assertNull(wrappingKeyCache.getIfPresent(cacheAlias))
        assertNotNull(countingWrappingStore.findWrappingKey(alias))
        assertNull(countingWrappingStore.findWrappingKey(unknownAlias))
        assertNull(countingWrappingStore.findWrappingKey(cacheAlias))

        assertThat(findCount).isEqualTo(7)

        assertThat(saveCount).isEqualTo(1)

        // stick a key in the cache underneath the SoftCryptoService 
        val knownWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        wrappingKeyCache.put(cacheAlias, knownWrappingKey)

        // now the cache and store should have alias and cacheAlias
        assertNotNull(wrappingKeyCache.getIfPresent(alias))
        assertNull(wrappingKeyCache.getIfPresent(unknownAlias))
        assertNotNull(wrappingKeyCache.getIfPresent(cacheAlias))
        assertNotNull(countingWrappingStore.findWrappingKey(alias))
        assertNull(countingWrappingStore.findWrappingKey(unknownAlias))
        assertNull(countingWrappingStore.findWrappingKey(cacheAlias))
    }
    
}