package net.corda.p2p.linkmanager

import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.test.stub.crypto.processor.UnsupportedAlgorithm
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringWriter
import java.security.Key
import java.security.KeyPairGenerator

class PublicKeyReaderTest {
    @Test
    fun `loadPublicKey throw exception for invalid pem`() {
        val reader = PublicKeyReader()

        assertThrows<PublicKeyReader.InvalidPem> {
            reader.loadPublicKey("nop")
        }
    }

    @Test
    fun `loadPublicKey throw exception for non public key pem`() {
        val reader = PublicKeyReader()
        val keys = KeyPairGenerator.getInstance("EC").genKeyPair()

        assertThrows<PublicKeyReader.InvalidPem> {
            reader.loadPublicKey(keys.private.toPem())
        }
    }

    @Test
    fun `loadPublicKey return the correct key`() {
        val reader = PublicKeyReader()
        val keys = KeyPairGenerator.getInstance("EC").genKeyPair()

        val key = reader.loadPublicKey(keys.public.toPem())

        assertThat(key).isEqualTo(keys.public)
    }

    @Test
    fun `toKeyAlgorithm return RSA for RSA key`() {
        val key = KeyPairGenerator.getInstance("RSA").genKeyPair().public

        assertThat(key.toKeyAlgorithm()).isEqualTo(KeyAlgorithm.RSA)
    }

    @Test
    fun `toKeyAlgorithm return EC for ECDSA key`() {
        val key = KeyPairGenerator.getInstance("EC").genKeyPair().public

        assertThat(key.toKeyAlgorithm()).isEqualTo(KeyAlgorithm.ECDSA)
    }

    @Test
    fun `toKeyAlgorithm throws exception for unsupported algorithm`() {
        val key = KeyPairGenerator.getInstance("DSA").genKeyPair().public

        assertThrows<UnsupportedAlgorithm> {
            key.toKeyAlgorithm()
        }
    }

    private fun Key.toPem(): String {
        return StringWriter().use { str ->
            JcaPEMWriter(str).use { writer ->
                writer.writeObject(this)
            }
            str.toString()
        }
    }
}
