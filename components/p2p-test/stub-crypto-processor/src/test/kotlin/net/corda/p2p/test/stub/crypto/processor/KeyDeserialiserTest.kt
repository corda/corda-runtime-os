package net.corda.p2p.test.stub.crypto.processor

import org.assertj.core.api.SoftAssertions.assertSoftly
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

class KeyDeserialiserTest {
    private val testObject = KeyDeserialiser()
    private val random = mock<SecureRandom> {
        on { nextBytes(any()) } doAnswer {
            val bytes = it.arguments[0] as ByteArray
            bytes.fill(3)
        }
    }
    private val keysFactory = KeyPairGenerator.getInstance("EC", BouncyCastleProvider()).also {
        it.initialize(ECGenParameterSpec("secp256k1"), random)
    }

    @Test
    fun `toKeyPair will throw exception for invalid PEM`() {
        assertThrows<CouldNotReadKey> {
            testObject.toKeyPair("PEM")
        }
    }

    @Test
    fun `toKeyPair will throw exception for non key pair`() {
        val keys = keysFactory.genKeyPair()

        assertThrows<CouldNotReadKey> {
            testObject.toKeyPair(keys.public.toPem())
        }
    }

    @Test
    fun `toKeyPair will return the correct pair`() {
        val actualKeys = keysFactory.genKeyPair()

        val keys = testObject.toKeyPair(actualKeys.toPem())

        assertSoftly {
            it.assertThat(keys.public).isEqualTo(actualKeys.public)
            it.assertThat(keys.private).isEqualTo(actualKeys.private)
        }
    }

    @Test
    fun `toKeyPair will ignore non keypair PEMs`() {
        val actualKeys = keysFactory.genKeyPair()
        val pems = actualKeys.public.toPem() + "\n" + actualKeys.public.toPem() + "\n" + actualKeys.toPem()

        val keys = testObject.toKeyPair(pems)

        assertSoftly {
            it.assertThat(keys.public).isEqualTo(actualKeys.public)
            it.assertThat(keys.private).isEqualTo(actualKeys.private)
        }
    }

    private fun Any.toPem(): String {
        return StringWriter().use { str ->
            JcaPEMWriter(str).use { writer ->
                writer.writeObject(this)
            }
            str.toString()
        }
    }
}
