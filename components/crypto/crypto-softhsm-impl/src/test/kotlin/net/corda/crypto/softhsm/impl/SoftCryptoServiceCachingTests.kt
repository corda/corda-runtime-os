package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.crypto.softhsm.impl.infra.makePrivateKeyCache
import net.corda.crypto.softhsm.impl.infra.makeWrappingKeyCache
import net.corda.v5.crypto.KeySchemeCodes.RSA_CODE_NAME
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.security.PrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * There is no way of unit testing the eviction in timely manner or without having special setup for the cache during
 * testing as by default the eviction is scheduled on an executor, so it's not exactly deterministic.
 */

class SoftCryptoServiceCachingTests {
    val schemeMetadata = CipherSchemeMetadataImpl()
    val rootWrappingKey = WrappingKey.generateWrappingKey(schemeMetadata)

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `getPrivateKey should cache requested key using public key as cache key`(cachePrivateKeys: Boolean) {
        var unwrapCounter = 0
        val privateKeyCache = if (cachePrivateKeys) makePrivateKeyCache(3) else null
        // Consider adding counter metrics to SoftCrytpoService so that we don't need to subclass and add
        // counters here
        val myCryptoService = object : SoftCryptoService(
            TestWrappingKeyStore(mock()),
            CipherSchemeMetadataImpl(),
            rootWrappingKey,
            null,
            privateKeyCache
        ) {
            override fun unwrapPrivateKey(key: WrappingKey, keyMaterial: ByteArray): PrivateKey {
                unwrapCounter++
                return key.unwrap(keyMaterial)
            }
        }
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
        val key21 = myCryptoService.getPrivateKey(key2.publicKey, key2Spec)
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
        Assertions.assertThat(unwrapCounter).isEqualTo(if (cachePrivateKeys) 2 else 4)
    }


    @Test
    fun `wrapPrivateKey should put to cache using public key as cache key`() {
        var unwrapCounter = 0
        var wrapCounter = 0
        val myCryptoService = object : SoftCryptoService(
            TestWrappingKeyStore(mock()),
            CipherSchemeMetadataImpl(),
            rootWrappingKey,
            makeWrappingKeyCache(),
            makePrivateKeyCache(3)
        ) {
            override fun wrapPrivateKey(wrappingKey: WrappingKey, privateKey: PrivateKey): ByteArray {
                wrapCounter++
                return wrappingKey.wrap(privateKey)
            }

            override fun unwrapPrivateKey(key: WrappingKey, keyMaterial: ByteArray): PrivateKey {
                unwrapCounter++
                return key.unwrap(keyMaterial)
            }
        }
        val scheme = myCryptoService.supportedSchemes.filter { it.key.codeName == RSA_CODE_NAME }.toList().first().first
        myCryptoService.createWrappingKey("master-alias", true, emptyMap())
        val key = myCryptoService.generateKeyPair(KeyGenerationSpec(scheme, "key-1", "master-alias"), emptyMap())
        val keySpec = KeyMaterialSpec(key.keyMaterial, "master-alias", key.encodingVersion)
        myCryptoService.getPrivateKey(key.publicKey, keySpec)
        assertThat(unwrapCounter).isEqualTo(0)
        assertThat(wrapCounter).isEqualTo(1)
    }
}