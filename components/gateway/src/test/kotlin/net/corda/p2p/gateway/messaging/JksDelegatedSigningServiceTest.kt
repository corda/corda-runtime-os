package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedCertificatesStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller.Companion.RSA_SIGNING_ALGORITHM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.util.Collections

class JksDelegatedSigningServiceTest {

    private val rsaCertificateAlias = "rc-name"
    private val ecCertificateAlias = "ec-name"
    private val privateKey = mock<PrivateKey>()
    private val rsaPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
    }
    private val ecPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "EC"
    }
    private val rsaCertificate = mock<Certificate> {
        on { publicKey } doReturn rsaPublicKey
    }
    private val ecCertificate = mock<Certificate> {
        on { publicKey } doReturn ecPublicKey
    }
    private val keyStoreSpi = mock<KeyStoreSpi> {
        on { engineAliases() } doReturn Collections.enumeration(listOf(rsaCertificateAlias, ecCertificateAlias))
        on { engineGetKey(any(), any()) } doReturn privateKey
        on { engineGetCertificateChain(rsaCertificateAlias) } doReturn arrayOf(rsaCertificate)
        on { engineGetCertificateChain(ecCertificateAlias) } doReturn arrayOf(ecCertificate)
    }
    private val jksService = mock<Provider.Service> {
        on { algorithm } doReturn "JKS"
        on { type } doReturn "KeyStore"
        on { newInstance(anyOrNull()) } doReturn keyStoreSpi
    }
    private val nonKeyStoreJksService = mock<Provider.Service> {
        on { algorithm } doReturn "JKS"
        on { type } doReturn "Yo"
        on { newInstance(anyOrNull()) } doReturn keyStoreSpi
    }
    private val nonJksKeyStoreService = mock<Provider.Service> {
        on { algorithm } doReturn "ALG"
        on { type } doReturn "KeyStore"
        on { newInstance(anyOrNull()) } doReturn keyStoreSpi
    }
    private val originalProvider = mock<Provider> {
        on { services } doReturn setOf(nonJksKeyStoreService, nonKeyStoreJksService, jksService)
    }
    private val rsaSignatureProvider = mock<Provider>()

    private val ecSignatureProvider = mock<Provider>()

    private val mockSecurity = mockStatic(Security::class.java).also {
        it.`when`<Array<Provider>> {
            Security.getProviders()
        }.doReturn(
            arrayOf(originalProvider)
        )
    }

    private val keyStore = mock<KeyStore>()
    private val mockKeyStore = mockStatic(KeyStore::class.java).also {
        it.`when`<KeyStore> {
            KeyStore.getInstance("name")
        }.doReturn(keyStore)
    }
    private val installationName = argumentCaptor<String>()
    private val signer = argumentCaptor<DelegatedSigner>()
    private val certificates = argumentCaptor<Collection<DelegatedCertificatesStore>>()
    private val installer = mock<DelegatedSignerInstaller> {
        on {
            install(
                installationName.capture(),
                signer.capture(),
                certificates.capture()
            )
        } doAnswer { }
        on { findOriginalSignatureProvider(RSA_SIGNING_ALGORITHM) } doReturn rsaSignatureProvider
        on { findOriginalSignatureProvider(DelegatedSigner.Hash.SHA256.ecName) } doReturn ecSignatureProvider
    }
    private val testObject = JksDelegatedSigningService(
        rawData = "hello".toByteArray(),
        password = "password",
        name = "name",
        installer = installer
    )

    @AfterEach
    fun cleanUp() {
        mockSecurity.close()
        mockKeyStore.close()
    }

    @Nested
    inner class AsKeyStoreTests {
        @Test
        fun `asKeyStore return the key store`() {
            assertThat(testObject.asKeyStore()).isSameAs(keyStore)
        }

        @Test
        fun `asKeyStore return the correct names for RSA certificate`() {
            testObject.asKeyStore()

            assertThat(certificates.firstValue.map { it.name }).contains(rsaCertificateAlias)
        }

        @Test
        fun `asKeyStore return the correct names for EC certificate`() {
            testObject.asKeyStore()

            assertThat(certificates.firstValue.map { it.name }).contains(ecCertificateAlias)
        }

        @Test
        fun `asKeyStore return the correct certificates for RSA certificate`() {
            testObject.asKeyStore()

            assertThat(certificates.firstValue.flatMap { it.certificates }).contains(rsaCertificate)
        }

        @Test
        fun `asKeyStore return the correct certificates for EC certificate`() {
            testObject.asKeyStore()

            assertThat(certificates.firstValue.flatMap { it.certificates }).contains(ecCertificate)
        }
        @Test
        fun `asKeyStore throw an exception when there is no valid provider`() {
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                emptyArray()
            )

            assertThrows<SecurityException> {
                testObject.asKeyStore()
            }
        }

        @Test
        fun `asKeyStore throw an exception when there is no valid service`() {
            whenever(jksService.newInstance(anyOrNull())).doReturn(null)

            assertThrows<SecurityException> {
                testObject.asKeyStore()
            }
        }

        @Test
        fun `asKeyStore load with the correct data`() {
            val data = argumentCaptor<InputStream>()
            val password = argumentCaptor<CharArray>()
            whenever(keyStoreSpi.engineLoad(data.capture(), password.capture())).doAnswer { }

            testObject.asKeyStore()

            assertThat(data.firstValue.readAllBytes()).isEqualTo("hello".toByteArray())
            assertThat(password.firstValue).isEqualTo("password".toCharArray())
        }
    }

    @Nested
    inner class SignTests {
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

        @Nested
        inner class RsaSignTests {
            @Test
            fun `rsa sign return the signature`() {
                val data = "data".toByteArray()
                val hash = DelegatedSigner.Hash.SHA256

                val sign = testObject.sign(rsaPublicKey, hash, data)

                assertThat(sign).isEqualTo("signature".toByteArray())
            }

            @Test
            fun `rsa sign sends the correct data`() {
                val data = "data".toByteArray()
                val hash = DelegatedSigner.Hash.SHA512

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
                val hash = DelegatedSigner.Hash.SHA256

                val sign = testObject.sign(ecPublicKey, hash, data)

                assertThat(sign).isEqualTo("signature".toByteArray())
            }

            @Test
            fun `ec sign sends the correct data`() {
                val data = "data".toByteArray()
                val hash = DelegatedSigner.Hash.SHA256

                testObject.sign(ecPublicKey, hash, data)

                verify(signature).initSign(privateKey)
                verify(signature).update(data)
            }

            @Test
            fun `ec sign with hash that has no provider will throw an exception`() {
                val data = "data".toByteArray()
                val hash = DelegatedSigner.Hash.SHA512

                assertThrows<SecurityException> {
                    testObject.sign(ecPublicKey, hash, data)
                }
            }
        }

        @Test
        fun `sign throws exception for unknown public key`() {
            val data = "data".toByteArray()
            val hash = DelegatedSigner.Hash.SHA256

            assertThrows<SecurityException> {
                testObject.sign(mock(), hash, data)
            }
        }

        @Test
        fun `sign throws exception for unknown algorithm`() {
            val unknownPublicKey = mock<PublicKey> {
                on { algorithm } doReturn "NOP"
            }
            val unknownCertificate = mock<Certificate> {
                on { publicKey } doReturn unknownPublicKey
            }
            whenever(keyStoreSpi.engineGetCertificateChain("nop"))
                .doReturn(arrayOf(unknownCertificate))
            whenever(keyStoreSpi.engineAliases())
                .doReturn(Collections.enumeration(listOf(rsaCertificateAlias, ecCertificateAlias, "nop")))
            val data = "data".toByteArray()
            val hash = DelegatedSigner.Hash.SHA256

            assertThrows<SecurityException> {
                testObject.sign(unknownPublicKey, hash, data)
            }
        }
    }
}
