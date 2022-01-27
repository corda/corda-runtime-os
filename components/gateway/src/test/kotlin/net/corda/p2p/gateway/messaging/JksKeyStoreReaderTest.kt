package net.corda.p2p.gateway.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.util.Collections

class JksKeyStoreReaderTest {
    private val certificatesOne = (1..3).map {
        mock<Certificate>()
    }
    private val certificatesTwo = (1..2).map {
        mock<Certificate>()
    }
    private val certificatesThree = (1..4).map {
        mock<Certificate>()
    }
    private val originalKeystore = mock<KeyStore> {
        on { aliases() } doReturn Collections.enumeration(
            listOf(
                "one",
                "two",
                "three",
                "four",
            )
        )
        on { getCertificateChain("one") } doReturn certificatesOne.toTypedArray()
        on { getCertificateChain("two") } doReturn certificatesTwo.toTypedArray()
        on { getCertificateChain("three") } doReturn certificatesThree.toTypedArray()
        on { getCertificateChain("four") } doReturn emptyArray()
    }
    private val mockKeyStore = mockStatic(KeyStore::class.java).also { mockSecurity ->
        mockSecurity.`when`<KeyStore> {
            KeyStore.getInstance("JKS")
        }.doReturn(originalKeystore)
    }
    private val testObject = JksKeyStoreReader("hello".toByteArray(), "password")

    @AfterEach
    fun cleanUp() {
        mockKeyStore.close()
    }

    @Nested
    inner class CertificateStoreTests {
        @Test
        fun `certificateStore return the correct certificates`() {
            val certificateStore = testObject.certificateStore

            assertThat(certificateStore.aliasToCertificates)
                .containsEntry(
                    "one", certificatesOne
                )
                .containsEntry(
                    "two", certificatesTwo
                )
                .containsEntry(
                    "three", certificatesThree
                )
        }

        @Test
        fun `certificateStore load the correct data`() {
            val data = argumentCaptor<InputStream>()
            val password = argumentCaptor<CharArray>()
            whenever(originalKeystore.load(data.capture(), password.capture())).doAnswer { }

            testObject.certificateStore.aliasToCertificates

            assertThat(data.firstValue.readAllBytes()).isEqualTo("hello".toByteArray())
            assertThat(password.firstValue).isEqualTo("password".toCharArray())
        }
    }
    @Nested
    inner class SignerTest {
        @Test
        fun `signer returns JksSigner`() {
            assertThat(testObject.signer).isInstanceOf(JksSigner::class.java)
        }

        @Test
        fun `signer create the correct keys map`() {
            val firstPublicKey = mock<PublicKey>()
            val lastPublicKey = mock<PublicKey>()
            val firstPrivateKey = mock<PrivateKey>()
            val lastPrivateKey = mock<PrivateKey>()
            whenever(certificatesOne.first().publicKey).doReturn(firstPublicKey)
            whenever(certificatesThree.first().publicKey).doReturn(lastPublicKey)
            whenever(originalKeystore.getKey("one", "password".toCharArray())).doReturn(firstPrivateKey)
            whenever(originalKeystore.getKey("three", "password".toCharArray())).doReturn(lastPrivateKey)

            var arguments: Map<PublicKey, PrivateKey>? = null
            mockConstruction(JksSigner::class.java) { _, context ->
                @Suppress("UNCHECKED_CAST")
                arguments = context.arguments()[0] as Map<PublicKey, PrivateKey>
            }.use {
                testObject.signer
            }

            assertThat(arguments)
                .containsEntry(firstPublicKey, firstPrivateKey)
                .containsEntry(lastPublicKey, lastPrivateKey)
                .hasSize(2)
        }
    }
}
