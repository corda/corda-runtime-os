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
import kotlin.test.assertSame

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
    fun `getPrivateKey should cache requested key using public key as cache key`(cachePrivateKeys: Boolean) {
        val privateKeyCache = if (cachePrivateKeys) makePrivateKeyCache() else null
        val myCryptoService = makeSoftCryptoService(privateKeyCache)
        val scheme = myCryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first
        myCryptoService.createWrappingKey("master-alias", true, emptyMap())
        val key1 = myCryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key-1", "master-alias"), emptyMap())
        val key2 = myCryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key-2", "master-alias"), emptyMap())
        val privateKey1 = privateKeyCache?.getIfPresent(key1.publicKey)
        val privateKey2 = privateKeyCache?.getIfPresent(key2.publicKey)
        privateKeyCache?.invalidate(key1.publicKey)
        privateKeyCache?.invalidate(key2.publicKey)
        val key1Spec = KeyMaterialSpec(key1.keyMaterial, "master-alias", key1.encodingVersion)
        val key2Spec = KeyMaterialSpec(key2.keyMaterial, "master-alias", key2.encodingVersion)
        val key11 = myCryptoService.getPrivateKey(key1.publicKey, key1Spec)
        val key11c = privateKeyCache?.getIfPresent(key1.publicKey)
        val key21 = myCryptoService.getPrivateKey(key2.publicKey, key2Spec)
        val key21c = privateKeyCache?.getIfPresent(key2.publicKey)
        if (cachePrivateKeys) {
            assertEquals(key11, key11c)
            assertEquals(key21, key21c)
        }
        val key12 = myCryptoService.getPrivateKey(key1.publicKey, key1Spec)
        val key22 = myCryptoService.getPrivateKey(key2.publicKey, key2Spec)
        assertNotSame(key11, key21)
        assertNotSame(key12, key22)
        if (cachePrivateKeys) {
            assertSame(key11, key12)
            assertSame(key21, key22)
        } else {
            // without caching we generally expect key11 and key12 to be different objects, but 
            // it seems they can sometimes be the same, which suggests that caffine even with cache size set to 0
            // sometimes can cache for a short period. So if we have assertNotSame(key11, key12) here it can fail.
            assertEquals(key11, key12)
            assertEquals(key21, key22)
        }
        // the keys we pulled out are reconstructed from encrypted key material, so are
        // not the same objects but are equal
        if (privateKey1 != null) {
            assertNotSame(key11, privateKey1)
            assertEquals(key11, privateKey1)
        }
        if (privateKey2 != null) {
            assertNotSame(key22, privateKey2)
            assertEquals(key21, privateKey2)
        }

        Assertions.assertThat(unwrapCount.get()).isEqualTo(if (cachePrivateKeys) 2 else 4)
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

    private fun makeSoftCryptoService(privateKeyCache: Cache<PublicKey, PrivateKey>?) =
        SoftCryptoService(
            TestWrappingKeyStore(mock()),
            schemeMetadata,
            rootWrappingKey,
            makeWrappingKeyCache(),
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