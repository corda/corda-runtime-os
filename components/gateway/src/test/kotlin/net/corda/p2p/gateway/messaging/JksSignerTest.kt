package net.corda.p2p.gateway.messaging

import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec

class JksSignerTest {
    private val rsaSignature = mock<Signature> {
        on { sign() } doReturn "RSA-Signature".toByteArray()
    }
    private val ecSignature = mock<Signature> {
        on { sign() } doReturn "EC-Signature".toByteArray()
    }
    private val mockSignature = mockStatic(Signature::class.java).also {
        it.`when`<Signature> {
            Signature.getInstance(any(), eq("SunRsaSign"))
        }.doReturn(rsaSignature)
        it.`when`<Signature> {
            Signature.getInstance(any(), eq("SunEC"))
        }.doReturn(ecSignature)
    }
    private val algorithmParameterSpec = mock<AlgorithmParameterSpec>()
    private val spec = SignatureSpec("signature-name", params = algorithmParameterSpec)

    @AfterEach
    fun cleanUp() {
        mockSignature.close()
    }
    private val ecPrivateKey = mock<PrivateKey>()
    private val rsaPrivateKey = mock<PrivateKey>()
    private val rsaPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
    }
    private val ecPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "EC"
    }

    private val testObject =
        JksSigner(
            mapOf(
                rsaPublicKey to rsaPrivateKey,
                ecPublicKey to ecPrivateKey,
            )
        )

    @Test
    fun `sign throws exception for unknown public key`() {
        val data = "data".toByteArray()

        assertThrows<SecurityException> {
            testObject.sign(mock(), spec, data)
        }
    }

    @Test
    fun `sign throws exception for unknown algorithm`() {
        val unknownPublicKey = mock<PublicKey> {
            on { algorithm } doReturn "NOP"
        }
        val data = "data".toByteArray()
        val testObject =
            JksSigner(
                mapOf(
                    unknownPublicKey to rsaPrivateKey,
                )
            )

        assertThrows<SecurityException> {
            testObject.sign(unknownPublicKey, spec, data)
        }
    }

    @Test
    fun `sign throws exception for null algorithm`() {
        val unknownPublicKey = mock<PublicKey> {
            on { algorithm } doReturn null
        }
        val data = "data".toByteArray()
        val testObject =
            JksSigner(
                mapOf(
                    unknownPublicKey to rsaPrivateKey,
                )
            )

        assertThrows<SecurityException> {
            testObject.sign(unknownPublicKey, spec, data)
        }
    }

    @Test
    fun `sign set the correct private key`() {
        val data = "data".toByteArray()

        testObject.sign(ecPublicKey, spec, data)

        verify(ecSignature).initSign(ecPrivateKey)
    }

    @Test
    fun `sign set the correct parameter`() {
        val data = "data".toByteArray()

        testObject.sign(rsaPublicKey, spec, data)

        verify(rsaSignature).setParameter(algorithmParameterSpec)
    }

    @Test
    fun `sign send the correct data`() {
        val data = "data".toByteArray()

        testObject.sign(ecPublicKey, spec, data)

        verify(ecSignature).update(data)
    }

    @Test
    fun `sign return the signature`() {
        val data = "data".toByteArray()

        val signData = testObject.sign(rsaPublicKey, spec, data)

        assertThat(signData).isEqualTo("RSA-Signature".toByteArray())
    }
}
