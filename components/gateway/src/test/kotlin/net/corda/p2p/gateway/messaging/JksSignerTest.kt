package net.corda.p2p.gateway.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Signature

class JksSignerTest {
    private val signature = mock<Signature> {
        on { sign() } doReturn "signature".toByteArray()
    }
    private val mockSignature = mockStatic(Signature::class.java).also {
        it.`when`<Signature> {
            Signature.getInstance(any(), any<Provider>())
        }.doReturn(signature)
    }

    @AfterEach
    fun cleanUp() {
        mockSignature.close()
    }
    private val privateKey = mock<PrivateKey>()
    private val publicKey = mock<PublicKey>()

    private val testObject =
        JksSigner(
            mapOf(
                publicKey to privateKey,
            )
        )
    @Test
    fun `sign return the signature`() {
        val data = "data".toByteArray()

        val sign = testObject.sign(publicKey, "", data)

        assertThat(sign).isEqualTo("signature".toByteArray())
    }

    @Test
    fun `sign sends the correct data`() {
        val data = "data".toByteArray()

        testObject.sign(publicKey, "", data)

        verify(signature).initSign(privateKey)
        verify(signature).update(data)
    }

    @Test
    fun `sign throws exception for unknown public key`() {
        val data = "data".toByteArray()

        assertThrows<SecurityException> {
            testObject.sign(mock(), "", data)
        }
    }

}
