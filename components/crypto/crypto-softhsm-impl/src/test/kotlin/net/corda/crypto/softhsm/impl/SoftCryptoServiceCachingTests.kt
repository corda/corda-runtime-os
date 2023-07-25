package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.TenantInfoService
import net.corda.crypto.softhsm.WrappingRepository
import net.corda.crypto.softhsm.impl.infra.CountingWrappingKey
import net.corda.crypto.softhsm.impl.infra.TestSigningRepository
import net.corda.crypto.softhsm.impl.infra.TestWrappingRepository
import net.corda.crypto.softhsm.impl.infra.makePrivateKeyCache
import net.corda.crypto.softhsm.impl.infra.makeShortHashCache
import net.corda.crypto.softhsm.impl.infra.makeSigningKeyInfoCache
import net.corda.crypto.softhsm.impl.infra.makeSoftCryptoService
import net.corda.crypto.softhsm.impl.infra.makeWrappingKeyCache
import net.corda.v5.base.util.EncodingUtils
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `private key unwrapping and identity works as expected`(cachePrivateKeys: Boolean) {
        // setup
        val privateKeyCache = if (cachePrivateKeys) makePrivateKeyCache() else null
        val wrappingKeyCache = makeWrappingKeyCache()
        val shortHashCache = makeShortHashCache()
        val signingKeyInfoCache = makeSigningKeyInfoCache()
        val wrappingKeyAlias = "wrapper1"
        val wrapCount = AtomicInteger()
        val unwrapCount = AtomicInteger()
        val schemeMetadata = CipherSchemeMetadataImpl()
        val rootWrappingKey =
            CountingWrappingKey(WrappingKeyImpl.generateWrappingKey(schemeMetadata), wrapCount, unwrapCount)

        val vnodeTenantId = EncodingUtils.toHex(UUID.randomUUID().toString().toByteArray().sha256Bytes()).take(12)
        val clusterWrappingRepository = TestWrappingRepository()
        val vnodeWrappingRepository = TestWrappingRepository()
        val tenantInfoService = mock<TenantInfoService>()
        val myCryptoService = SoftCryptoService(
            privateKeyCache = privateKeyCache,
            wrappingKeyCache = wrappingKeyCache,
            shortHashCache = shortHashCache,
            defaultUnmanagedWrappingKeyName = "root",
            unmanagedWrappingKeys = mapOf("root" to rootWrappingKey),
            wrappingKeyFactory = { metadata: CipherSchemeMetadata ->
                CountingWrappingKey(
                    WrappingKeyImpl.generateWrappingKey(metadata),
                    wrapCount,
                    unwrapCount
                )
            },
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            schemeMetadata = schemeMetadata,
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            wrappingRepositoryFactory = {
                when (it) {
                    CryptoTenants.CRYPTO -> clusterWrappingRepository
                    else -> vnodeWrappingRepository
                }
            },
            signingRepositoryFactory = { TestSigningRepository() },
            signingKeyInfoCache = signingKeyInfoCache,
            tenantInfoService = tenantInfoService
        )
        val rsaScheme =
            myCryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first

        // set up a second level wrapping key
        assertFalse(vnodeWrappingRepository.keys.contains(wrappingKeyAlias))
        myCryptoService.createWrappingKey(wrappingKeyAlias, true, mapOf("tenantId" to vnodeTenantId))
        assertFalse(clusterWrappingRepository.keys.contains(wrappingKeyAlias))
        assertThat(vnodeWrappingRepository.keys.contains(wrappingKeyAlias))
        val wrappingKey = wrappingKeyCache.getIfPresent(wrappingKeyAlias)
        assertNotNull(wrappingKey)

        // make two key pairs
        val keyPairs = arrayOf(0, 1).map {
            myCryptoService.generateKeyPair(
                vnodeTenantId,
                CryptoConsts.Categories.LEDGER,
                "key-$it",
                null,
                rsaScheme,
                mapOf("parentKeyAlias" to wrappingKeyAlias)
            )
        }

        // if we have a private key cache, it should now have entries for both key pairs
        val privateKey1FromCache = privateKeyCache?.getIfPresent(keyPairs[0].publicKey)
        val privateKey2FromCache = privateKeyCache?.getIfPresent(keyPairs[1].publicKey)

        if (privateKeyCache != null) {
            assertNotNull(privateKey1FromCache)
            assertNotNull(privateKey2FromCache)
        }

        // clear the private key cache, if there is one
        keyPairs.forEach { privateKeyCache?.invalidate(it.publicKey) }

        // make spec objects with the public key material 
        val keySpecs = keyPairs.map { KeyMaterialSpec(it.keyMaterial, wrappingKeyAlias, it.encodingVersion) }

        val keysDirect = keySpecs.map { wrappingKey.unwrap(it.keyMaterial) }

        // the keys we pulled out are reconstructed from encrypted key material, so are
        // not the same objects but are equal to what we got from the cache before
        if (privateKey1FromCache != null) {
            assertNotSame(keysDirect[0], privateKey1FromCache)
            assertEquals(keysDirect[0], privateKey1FromCache)

        }
        if (privateKey2FromCache != null) {
            assertNotSame(keysDirect[1], privateKey2FromCache)
            assertEquals(keysDirect[1], privateKey2FromCache)
        }

        assertThat(unwrapCount.get()).isEqualTo(2)
    }


    @Test
    fun `wrapPrivateKey should put to cache using public key as cache key`() {
        val privateKeyCache = makePrivateKeyCache()
        val wrapCount = AtomicInteger()
        val unwrapCount = AtomicInteger()
        val schemeMetadata = CipherSchemeMetadataImpl()
        val rootWrappingKey =
            CountingWrappingKey(WrappingKeyImpl.generateWrappingKey(schemeMetadata), wrapCount, unwrapCount)

        val myCryptoService =
            makeSoftCryptoService(
                privateKeyCache = privateKeyCache,
                wrappingKeyCache = null,
                schemeMetadata = schemeMetadata,
                rootWrappingKey = rootWrappingKey,
                wrappingKeyFactory = {
                    CountingWrappingKey(
                        WrappingKeyImpl.generateWrappingKey(it),
                        wrapCount,
                        unwrapCount
                    )
                }
            )
        myCryptoService.createWrappingKey("master-alias", true, emptyMap())
        val scheme = myCryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first
        val key = myCryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key-1", "master-alias"), emptyMap())
        val privateKeyFromCache = privateKeyCache.getIfPresent(key.publicKey)
        assertNotNull(privateKeyFromCache)
        assertThat(unwrapCount.get()).isEqualTo(0)
        assertThat(wrapCount.get()).isEqualTo(2)
    }


    @Test
    fun `createWrappingKey should put to cache using public key as cache key`() {
        val schemeMetadata = CipherSchemeMetadataImpl()
        val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)

        val alias = UUID.randomUUID().toString()
        val unknownAlias = UUID.randomUUID().toString()
        val cacheAlias = UUID.randomUUID().toString()

        var saveCount = 0
        var findCount = 0
        val testCryptoRepositoryWrapping = TestWrappingRepository()
        val countingWrappingRepository: WrappingRepository = object : WrappingRepository {
            override fun saveKey(alias: String, key: WrappingKeyInfo): WrappingKeyInfo {
                saveCount++
                return testCryptoRepositoryWrapping.saveKey(alias, key)
            }

            override fun findKey(alias: String): WrappingKeyInfo? {
                findCount++
                return testCryptoRepositoryWrapping.findKey(alias)
            }

            override fun close() {
            }
        }
        val wrappingKeyCache = makeWrappingKeyCache()
        val myCryptoService = makeSoftCryptoService(
            wrappingRepository = countingWrappingRepository,
            schemeMetadata = schemeMetadata,
            rootWrappingKey = rootWrappingKey,
            wrappingKeyCache = wrappingKeyCache,
            privateKeyCache = makePrivateKeyCache(),
        )

        // starting fresh, all 3 aliases are missing from both store and cache
        assertNull(wrappingKeyCache.getIfPresent(alias))
        assertNull(wrappingKeyCache.getIfPresent(unknownAlias))
        assertNull(wrappingKeyCache.getIfPresent(cacheAlias))
        assertNull(countingWrappingRepository.findKey(alias))
        assertNull(countingWrappingRepository.findKey(unknownAlias))
        assertNull(countingWrappingRepository.findKey(cacheAlias))

        assertThat(findCount).isEqualTo(3)
        assertThat(saveCount).isEqualTo(0)

        myCryptoService.createWrappingKey(alias, true, mapOf())

        assertThat(findCount).isEqualTo(4) // we do a find to check for conflicts
        assertThat(saveCount).isEqualTo(1)

        // now the cache and store should have alias but not the other two
        assertNotNull(wrappingKeyCache.getIfPresent(alias))
        assertNull(wrappingKeyCache.getIfPresent(unknownAlias))
        assertNull(wrappingKeyCache.getIfPresent(cacheAlias))
        assertNotNull(countingWrappingRepository.findKey(alias))
        assertNull(countingWrappingRepository.findKey(unknownAlias))
        assertNull(countingWrappingRepository.findKey(cacheAlias))

        assertThat(findCount).isEqualTo(7)

        assertThat(saveCount).isEqualTo(1)

        // stick a key in the cache underneath the SoftCryptoService 
        val knownWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        wrappingKeyCache.put(cacheAlias, knownWrappingKey)

        // now the cache and store should have alias and cacheAlias
        assertNotNull(wrappingKeyCache.getIfPresent(alias))
        assertNull(wrappingKeyCache.getIfPresent(unknownAlias))
        assertNotNull(wrappingKeyCache.getIfPresent(cacheAlias))
        assertNotNull(countingWrappingRepository.findKey(alias))
        assertNull(countingWrappingRepository.findKey(unknownAlias))
        assertNull(countingWrappingRepository.findKey(cacheAlias))
    }
    
    @Test
    fun `Lookup by short hashes of keys for multiple tenants which are cached does not go to database`() {
        val schemeMetadata = CipherSchemeMetadataImpl()
        val tenants = listOf(0,1)
        val tenantIds = tenants.map { UUID.randomUUID().toString() }
        val knownWrappingKeyAliases = tenants.map { UUID.randomUUID().toString() }
        val rootWrappingKey = WrappingKeyImpl.generateWrappingKey(schemeMetadata)
        val knownWrappingKeys= tenants.map { WrappingKeyImpl.generateWrappingKey(schemeMetadata) }
        val knownWrappingKeyMaterials = knownWrappingKeys.map { rootWrappingKey.wrap(it) }
        
        val tenantWrappingRepositories = tenants.map {
            TestWrappingRepository(
                ConcurrentHashMap(
                    listOf(
                        knownWrappingKeyAliases.elementAt(it) to WrappingKeyInfo(
                            WRAPPING_KEY_ENCODING_VERSION,
                            knownWrappingKeys.elementAt(it).algorithm,
                            knownWrappingKeyMaterials.elementAt(it),
                            1,
                            "root",
                        )
                    ).toMap()
                )
            )
        }        
        val shortHashCache = makeShortHashCache()
        val cryptoService = SoftCryptoService(
            wrappingRepositoryFactory= {  tenantId -> tenantWrappingRepositories.elementAt(tenantIds.indexOf(tenantId)) },
            schemeMetadata = schemeMetadata,
            privateKeyCache = makePrivateKeyCache(),
            shortHashCache = shortHashCache,
            signingKeyInfoCache = makeSigningKeyInfoCache(),
            keyPairGeneratorFactory = { algorithm: String, provider: Provider ->
                KeyPairGenerator.getInstance(algorithm, provider)
            },
            wrappingKeyFactory = { it -> WrappingKeyImpl.generateWrappingKey(it) },
            wrappingKeyCache = makeWrappingKeyCache(),
            signingRepositoryFactory = { TestSigningRepository(it) },
            defaultUnmanagedWrappingKeyName = "root",
            digestService = PlatformDigestServiceImpl(schemeMetadata),
            tenantInfoService = mock(),
            unmanagedWrappingKeys = mapOf("root" to rootWrappingKey)
            )
        val rsa = cryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first
        val keyPairs = tenants.map {
            cryptoService.generateKeyPair(
                tenantIds.elementAt(it),
                CryptoConsts.Categories.LEDGER,
                "key-$it",
                null,
                rsa,
                mapOf("parentKeyAlias" to knownWrappingKeyAliases.elementAt(it))
            )
        }
        val shortHashes = tenants.map {
            val key = keyPairs.elementAt(it).publicKey
            makeShortHash(schemeMetadata, key)
        }
        tenants.map {
            assertThat( shortHashCache.getIfPresent(shortHashes.elementAt(it))).isNotNull()
        }
        assertThat(shortHashCache.getIfPresent(makeShortHash(schemeMetadata,  keyPairs.elementAt(0).publicKey)))
        val found1of1full = cryptoService.lookupSigningKeysByPublicKeyHashes(tenantIds.elementAt(0), 
            listOf(keyPairs.elementAt(0).publicKey.fullIdHash()))
        assertThat(found1of1full.size).isEqualTo(1)
        assertThat(found1of1full.elementAt(0).publicKey).isEqualTo(keyPairs.elementAt(0).publicKey)
        val found1of1short = cryptoService.lookupSigningKeysByPublicKeyShortHash(tenantIds.elementAt(0),
            listOf(shortHashes.elementAt(0)))
        assertThat(found1of1short.size).isEqualTo(1)
        assertThat(found1of1short.elementAt(0).publicKey).isEqualTo(keyPairs.elementAt(0).publicKey)
        val found1of2short = cryptoService.lookupSigningKeysByPublicKeyShortHash(tenantIds.elementAt(0), 
            shortHashes)
        assertThat(found1of2short.size).isEqualTo(1)
        assertThat(found1of2short.elementAt(0).publicKey).isEqualTo(keyPairs.elementAt(0).publicKey)
        val found1of2full = cryptoService.lookupSigningKeysByPublicKeyHashes(tenantIds.elementAt(1), 
            keyPairs.map { it.publicKey.fullIdHash() })
        assertThat(found1of2full.size).isEqualTo(1)
        assertThat(found1of2full.elementAt(0).publicKey).isEqualTo(keyPairs.elementAt(1).publicKey)
    }

    private fun makeShortHash(
        schemeMetadata: CipherSchemeMetadataImpl,
        key: PublicKey
    ): ShortHash {
        val keyBytes = schemeMetadata.encodeAsByteArray(key)
        return ShortHash.of(publicKeyIdFromBytes(keyBytes))
    }

}