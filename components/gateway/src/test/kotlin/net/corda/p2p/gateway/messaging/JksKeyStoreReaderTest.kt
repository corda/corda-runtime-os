package net.corda.p2p.gateway.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
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
    private val originalSpi = mock<KeyStoreSpi> {
        on { engineAliases() } doReturn Collections.enumeration(
            listOf(
                "one",
                "two",
                "three",
                "four",
            )
        )
        on { engineGetCertificateChain("one") } doReturn certificatesOne.toTypedArray()
        on { engineGetCertificateChain("two") } doReturn certificatesTwo.toTypedArray()
        on { engineGetCertificateChain("three") } doReturn certificatesThree.toTypedArray()
        on { engineGetCertificateChain("four") } doReturn emptyArray()
    }
    private val service = mock<Provider.Service> {
        on { newInstance(null) } doReturn originalSpi
    }
    private val provider = mock<Provider> {
        on { getService("KeyStore", "JKS") } doReturn service
    }
    private val mockSecurity = mockStatic(Security::class.java).also { mockSecurity ->
        mockSecurity.`when`<Provider> {
            Security.getProvider("SUN")
        }.doReturn(provider)
    }
    private val testObject = JksKeyStoreReader("hello".toByteArray(), "password")

    @AfterEach
    fun cleanUp() {
        mockSecurity.close()
    }

    @Nested
    inner class CertificatesTest {
        @Test
        fun `certificates return the corect certificates`() {
            val certificates = testObject.certificates

            assertThat(certificates.aliasToCertificates)
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
        fun `certificates throws exception if provider can not be found`() {
            mockSecurity.`when`<Provider> {
                Security.getProvider("SUN")
            }.doReturn(null)

            assertThrows<SecurityException> {
                testObject.certificates.aliasToCertificates
            }
        }

        @Test
        fun `certificates throws exception if provider has no service`() {
            whenever(provider.getService("KeyStore", "JKS")).doReturn(null)

            assertThrows<SecurityException> {
                testObject.certificates.aliasToCertificates
            }
        }

        @Test
        fun `certificates throws exception if service return wrong instance type`() {
            whenever(provider.getService("KeyStore", "JKS")).doReturn(mock())

            assertThrows<SecurityException> {
                testObject.certificates.aliasToCertificates
            }
        }

        @Test
        fun `certificates load the correct data`() {
            val data = argumentCaptor<InputStream>()
            val password = argumentCaptor<CharArray>()
            whenever(originalSpi.engineLoad(data.capture(), password.capture())).doAnswer { }

            testObject.certificates.aliasToCertificates

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
            whenever(originalSpi.engineGetKey("one", "password".toCharArray())).doReturn(firstPrivateKey)
            whenever(originalSpi.engineGetKey("three", "password".toCharArray())).doReturn(lastPrivateKey)

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
