package net.corda.p2p.test.stub.crypto.processor

import net.corda.p2p.test.KeyAlgorithm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class KeyDeserialiserTest {
    private val rsaKeyFactory = mock<KeyFactory>()
    private val ecdsaKeyFactory = mock<KeyFactory>()
    private val testObject = KeyDeserialiser {
        when (it) {
            "RSA" -> rsaKeyFactory
            "EC" -> ecdsaKeyFactory
            else -> mock()
        }
    }

    @Test
    fun `toPrivateKey returns the correct private key`() {
        val privateKey = mock<PrivateKey>()
        whenever(rsaKeyFactory.generatePrivate(any())).doReturn(privateKey)

        val key = testObject.toPrivateKey("data".toByteArray(), KeyAlgorithm.RSA)

        assertThat(key).isEqualTo(privateKey)
    }

    @Test
    fun `toPrivateKey sends the correct data`() {
        val keySpec = argumentCaptor<KeySpec>()
        whenever(rsaKeyFactory.generatePrivate(keySpec.capture())).doReturn(mock())

        testObject.toPrivateKey("data".toByteArray(), KeyAlgorithm.RSA)

        val data = (keySpec.firstValue as? PKCS8EncodedKeySpec)?.encoded
        assertThat(data).isEqualTo("data".toByteArray())
    }

    @Test
    fun `toPublicKey returns the correct public key`() {
        val publicKey = mock<PublicKey>()
        whenever(ecdsaKeyFactory.generatePublic(any())).doReturn(publicKey)

        val key = testObject.toPublicKey("data".toByteArray(), KeyAlgorithm.ECDSA)

        assertThat(key).isEqualTo(publicKey)
    }

    @Test
    fun `toPublicKey sends the correct data`() {
        val keySpec = argumentCaptor<KeySpec>()
        whenever(ecdsaKeyFactory.generatePublic(keySpec.capture())).doReturn(mock())

        testObject.toPublicKey("data".toByteArray(), KeyAlgorithm.ECDSA)

        val data = (keySpec.firstValue as? X509EncodedKeySpec)?.encoded
        assertThat(data).isEqualTo("data".toByteArray())
    }
}
