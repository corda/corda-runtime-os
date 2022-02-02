package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.Hash
import net.corda.crypto.delegated.signing.SigningParameter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

class JksSignerTest {
    private val signature = mock<Signature> {
        on { sign() } doReturn "signature".toByteArray()
    }
    private val mockSignature = mockStatic(Signature::class.java).also {
        it.`when`<Signature> {
            Signature.getInstance(any(), any<String>())
        }.doReturn(signature)
    }

    @AfterEach
    fun cleanUp() {
        mockSignature.close()
    }
    private val privateKey = mock<PrivateKey>()
    private val rsaPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
    }
    private val ecPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "EC"
    }

    private val testObject =
        JksSigner(
            mapOf(
                rsaPublicKey to privateKey,
                ecPublicKey to privateKey
            )
        )

    @Nested
    inner class RsaSignTests {
        @Test
        fun `rsa sign return the signature`() {
            val data = "data".toByteArray()
            val hash = Hash.SHA256

            val sign = testObject.sign(rsaPublicKey, hash, data)

            assertThat(sign).isEqualTo("signature".toByteArray())
        }

        @Test
        fun `rsa sign sends the correct data`() {
            val data = "data".toByteArray()
            val hash = Hash.SHA512

            testObject.sign(rsaPublicKey, hash, data)

            verify(signature).initSign(privateKey)
            verify(signature).setParameter(hash.rsaParameter)
            verify(signature).update(data)
        }
    }

    @Nested
    inner class EcSignTests {
        @Test
        fun `ec sign return the signature`() {
            val data = "data".toByteArray()
            val hash = Hash.SHA256

            val sign = testObject.sign(ecPublicKey, hash, data)

            assertThat(sign).isEqualTo("signature".toByteArray())
        }

        @Test
        fun `ec sign sends the correct data`() {
            val data = "data".toByteArray()
            val hash = Hash.SHA256

            testObject.sign(ecPublicKey, hash, data)

            verify(signature).initSign(privateKey)
            verify(signature).update(data)
        }
    }

    @Test
    fun `sign throws exception for unknown public key`() {
        val data = "data".toByteArray()
        val hash = Hash.SHA256

        assertThrows<SecurityException> {
            testObject.sign(mock(), hash, data)
        }
    }

    @Test
    fun `sign throws exception for unknown parameter`() {
        val data = "data".toByteArray()
        val parameter = object : SigningParameter {}

        assertThrows<SecurityException> {
            testObject.sign(ecPublicKey, parameter, data)
        }
    }

    @Test
    fun `sign throws exception for unknown algorithm`() {
        val unknownPublicKey = mock<PublicKey> {
            on { algorithm } doReturn "NOP"
        }
        val data = "data".toByteArray()
        val hash = Hash.SHA256
        val testObject =
            JksSigner(
                mapOf(
                    unknownPublicKey to privateKey,
                )
            )

        assertThrows<SecurityException> {
            testObject.sign(unknownPublicKey, hash, data)
        }
    }
    @Test
    fun `sign throws exception for null algorithm`() {
        val unknownPublicKey = mock<PublicKey> {
            on { algorithm } doReturn null
        }
        val data = "data".toByteArray()
        val hash = Hash.SHA256
        val testObject =
            JksSigner(
                mapOf(
                    unknownPublicKey to privateKey,
                )
            )

        assertThrows<SecurityException> {
            testObject.sign(unknownPublicKey, hash, data)
        }
    }
}
