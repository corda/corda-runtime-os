package net.corda.crypto.service.impl

import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.softhsm.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.softhsm.PrivateKeyMaterial
import net.corda.crypto.softhsm.SoftPrivateKeyWrapping
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.test.assertSame

class TransientSoftKeyMapTests {
    @Test
    fun `Should get private key`() {
        val publicKey = mock<PublicKey>()
        val expected = mock<PrivateKey>()
        val spec = KeyMaterialSpec(
            ByteArray(1),
            "master-alias",
            PRIVATE_KEY_ENCODING_VERSION
        )
        val wrapping = mock<SoftPrivateKeyWrapping> {
            on { unwrap(spec) } doReturn expected
        }
        val cut = TransientSoftKeyMap(wrapping)
        val key1 = cut.getPrivateKey(publicKey, spec)
        assertSame(expected, key1)
        val key2 = cut.getPrivateKey(publicKey, spec)
        assertSame(expected, key2)
        Mockito.verify(wrapping, times(2)).unwrap(any())
    }

    @Test
    fun `Should wrap private key`() {
        val keyPair = KeyPair(mock(), mock())
        val expected = PrivateKeyMaterial(
            PRIVATE_KEY_ENCODING_VERSION,
            ByteArray(1)
        )
        val wrapping = mock<SoftPrivateKeyWrapping> {
            on { wrap(keyPair.private, "master-alias") } doReturn expected
            on { unwrap(any()) } doReturn keyPair.private
        }
        val cut = TransientSoftKeyMap(wrapping)
        val keyMaterial = cut.wrapPrivateKey(keyPair, "master-alias")
        assertSame(expected, keyMaterial)
        Mockito.verify(wrapping, times(1)).wrap(any(), any())
        val key = cut.getPrivateKey(
            keyPair.public, KeyMaterialSpec(
                keyMaterial.keyMaterial,
                "master-alias",
                keyMaterial.encodingVersion
            )
        )
        assertSame(keyPair.private, key)
        Mockito.verify(wrapping, times(1)).unwrap(
            argThat {
                this.encodingVersion == PRIVATE_KEY_ENCODING_VERSION &&
                        this.masterKeyAlias == "master-alias" &&
                        this.keyMaterial === keyMaterial.keyMaterial
            }
        )
        Mockito.verify(wrapping, times(1)).unwrap(any())
    }
}