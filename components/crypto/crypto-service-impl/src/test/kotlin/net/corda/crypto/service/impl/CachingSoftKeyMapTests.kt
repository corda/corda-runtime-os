package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.service.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.service.PrivateKeyMaterial
import net.corda.crypto.service.SoftPrivateKeyWrapping
import net.corda.crypto.softhsm.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.softhsm.PrivateKeyMaterial
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * There is no way of unit testing the eviction in timely manner or without having special setup for the cache during
 * testing as by default the eviction is scheduled on an executor, so it's not exactly deterministic.
 */
class CachingSoftKeyMapTests {
    @Test
    fun `getPrivateKey should cache requested key using public key as cache key`() {
        val expected1 = mock<PrivateKey>()
        val expected2 = mock<PrivateKey>()
        val publicKey1 = mock<PublicKey>()
        val publicKey2 = mock<PublicKey>()
        val spec1 = KeyMaterialSpec(ByteArray(1), "master-alias", PRIVATE_KEY_ENCODING_VERSION)
        val spec2 = KeyMaterialSpec(ByteArray(1), "master-alias", PRIVATE_KEY_ENCODING_VERSION)
        val wrapping = mock<SoftPrivateKeyWrapping> {
            on { unwrap(spec1) } doReturn expected1
            on { unwrap(spec2) } doReturn expected2
        }
        val cut = CachingSoftKeyMap(
            SoftCacheConfig(expireAfterAccessMins = 2, maximumSize = 3),
            wrapping
        )

        val key11 = cut.getPrivateKey(publicKey1, spec1)
        assertSame(expected1, key11)
        val key21 = cut.getPrivateKey(publicKey2, spec2)
        assertSame(expected2, key21)
        assertNotSame(key11, key21)

        val key12 = cut.getPrivateKey(publicKey1, spec1)
        assertSame(expected1, key12)
        val key22 = cut.getPrivateKey(publicKey2, spec2)
        assertSame(expected2, key22)

        Mockito.verify(wrapping, times(2)).unwrap(any())
    }

    @Test
    fun `wrapPrivateKey should put to cache using public key as cache key`() {
        val keyPair = KeyPair(
            mock(),
            mock()
        )
        val privateKeyMaterial = PrivateKeyMaterial(PRIVATE_KEY_ENCODING_VERSION, ByteArray(1))
        val spec = KeyMaterialSpec(
            privateKeyMaterial.keyMaterial,
            "master-alias",
            PRIVATE_KEY_ENCODING_VERSION
        )
        val wrapping = mock<SoftPrivateKeyWrapping> {
            on { unwrap(spec) } doReturn keyPair.private
            on { wrap(keyPair.private, "master-alias") } doReturn privateKeyMaterial
        }
        val cut = CachingSoftKeyMap(
            SoftCacheConfig(expireAfterAccessMins = 1, maximumSize = 3),
            wrapping
        )
        val keyMaterial = cut.wrapPrivateKey(keyPair, "master-alias")
        assertSame(privateKeyMaterial, keyMaterial)
        val key = cut.getPrivateKey(keyPair.public, spec)
        assertSame(keyPair.private, key)
        Mockito.verify(wrapping, times(1)).wrap(any(), any())
        Mockito.verify(wrapping, never()).unwrap(any())
    }
}