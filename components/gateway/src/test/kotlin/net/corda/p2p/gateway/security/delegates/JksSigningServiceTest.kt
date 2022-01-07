package net.corda.p2p.gateway.security.delegates

import net.corda.p2p.gateway.security.delegates.SecurityDelegateProvider.RSA_SINGING_ALGORITHM
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStoreSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.Certificate
import java.util.Collections

class JksSigningServiceTest {
    private val myProvider = mock<SecurityDelegateProvider>()
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
    private val keyStoreSpi = mock< KeyStoreSpi> {
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
    private val rsaSignatureService = mock<Provider.Service> {
        on { algorithm } doReturn RSA_SINGING_ALGORITHM
        on { type } doReturn "Signature"
    }
    private val rsaNonSignatureService = mock<Provider.Service> {
        on { algorithm } doReturn RSA_SINGING_ALGORITHM
        on { type } doReturn "NotSignature"
    }
    private val rsaSignatureProvider = mock<Provider> {
        on { services } doReturn setOf(rsaNonSignatureService, rsaSignatureService)
    }
    private val ecSignatureServices = SigningService.Hash.values().flatMap { hash ->
        listOf(
            mock<Provider.Service> {
                on { algorithm } doReturn hash.ecName
                on { type } doReturn "NotSignature"
            },
            mock {
                on { algorithm } doReturn hash.ecName
                on { type } doReturn "Signature"
            }
        )
    }

    private val ecSignatureProvider = mock<Provider> {
        on { services } doReturn ecSignatureServices.toSet()
    }

    private val mockSecurity = mockStatic(Security::class.java).also {
        it.`when`<Array<Provider>> {
            Security.getProviders()
        }.doReturn(
            arrayOf(myProvider, originalProvider, rsaSignatureProvider, ecSignatureProvider)
        )

        whenever(rsaSignatureService.provider).doReturn(rsaSignatureProvider)
        ecSignatureServices.forEach {
            whenever(it.provider).doReturn(ecSignatureProvider)
        }
    }

    @AfterEach
    fun cleanUp() {
        mockSecurity.close()
    }

    @Nested
    inner class AliasTests {
        @Test
        fun `alias return the correct names for RSA certificate`() {
            val service = JksSigningService(ByteArray(0), "password")

            assertThat(service.aliases.map { it.name }).contains(rsaCertificateAlias)
        }

        @Test
        fun `alias return the correct names for CE certificate`() {
            val service = JksSigningService(ByteArray(0), "password")

            assertThat(service.aliases.map { it.name }).contains(ecCertificateAlias)
        }

        @Test
        fun `alias return the correct certificates for RSA certificate`() {
            val service = JksSigningService(ByteArray(0), "password")

            assertThat(service.aliases.flatMap { it.certificates }).contains(rsaCertificate)
        }

        @Test
        fun `alias return the correct certificates for CE certificate`() {
            val service = JksSigningService(ByteArray(0), "password")

            assertThat(service.aliases.flatMap { it.certificates }).contains(ecCertificate)
        }

        @Test
        fun `aliases throw an exception when there is no valid provider`() {
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                emptyArray()
            )
            val service = JksSigningService(ByteArray(0), "password")

            assertThrows<SecurityException> {
                service.aliases
            }
        }

        @Test
        fun `aliases throw an exception when there is no valid service`() {
            whenever(jksService.newInstance(anyOrNull())).doReturn(null)
            val service = JksSigningService(ByteArray(0), "password")

            assertThrows<SecurityException> {
                service.aliases
            }
        }

        @Test
        fun `aliases load with the correct data`() {
            val data = argumentCaptor<InputStream>()
            val password = argumentCaptor<CharArray>()
            whenever(keyStoreSpi.engineLoad(data.capture(), password.capture())).doAnswer { }
            val service = JksSigningService("hello".toByteArray(), "password")

            service.aliases

            assertThat(data.firstValue.readAllBytes()).isEqualTo("hello".toByteArray())
            assertThat(password.firstValue).isEqualTo("password".toCharArray())
        }
    }

    @Nested
    inner class SignTests {
        @Nested
        inner class RsaSignTests {
            @Test
            fun `rsa sign return the signature`() {
                val data = "data".toByteArray()
                val hash = SigningService.Hash.SHA384
                val service = JksSigningService("hello".toByteArray(), "password")
                val alias = service.aliases.first { it.name == rsaCertificateAlias }
                val signature = mock<Signature> {
                    on { sign() } doReturn "signature".toByteArray()
                }
                val sign = mockStatic(Signature::class.java).use { mockSignature ->
                    mockSignature.`when`<Signature> {
                        Signature.getInstance(
                            RSA_SINGING_ALGORITHM,
                            rsaSignatureProvider
                        )
                    }.doReturn(
                        signature
                    )

                    alias.sign(hash, data)
                }

                assertThat(sign).isEqualTo("signature".toByteArray())
            }

            @Test
            fun `rsa sign sends the correct data`() {
                val data = "data".toByteArray()
                val hash = SigningService.Hash.SHA512
                val service = JksSigningService("hello".toByteArray(), "password")
                val alias = service.aliases.first { it.name == rsaCertificateAlias }
                val signature = mock<Signature> {
                    on { sign() } doReturn "signature".toByteArray()
                }
                mockStatic(Signature::class.java).use { mockSignature ->
                    mockSignature.`when`<Signature> {
                        Signature.getInstance(
                            RSA_SINGING_ALGORITHM,
                            rsaSignatureProvider
                        )
                    }.doReturn(
                        signature
                    )

                    alias.sign(hash, data)
                }

                verify(signature).initSign(privateKey)
                verify(signature).setParameter(hash.rsaParameter)
                verify(signature).update(data)
            }

            @Test
            fun `rsa sign will fail if it cant find the original service provider`() {
                mockSecurity.`when`<Array<Provider>> {
                    Security.getProviders()
                }.doReturn(
                    arrayOf(myProvider, originalProvider, ecSignatureProvider)
                )
                val data = "data".toByteArray()
                val hash = SigningService.Hash.SHA384
                val service = JksSigningService("hello".toByteArray(), "password")
                val alias = service.aliases.first { it.name == rsaCertificateAlias }
                val signature = mock<Signature>()
                mockStatic(Signature::class.java).use { mockSignature ->
                    mockSignature.`when`<Signature> {
                        Signature.getInstance(
                            RSA_SINGING_ALGORITHM,
                            rsaSignatureProvider
                        )
                    }.doReturn(
                        signature
                    )

                    assertThrows<SecurityException> {
                        alias.sign(hash, data)
                    }
                }
            }
        }

        @Nested
        inner class EcSignTests {
            @Test
            fun `ec sign return the signature`() {
                val data = "data".toByteArray()
                val hash = SigningService.Hash.SHA256
                val service = JksSigningService("hello".toByteArray(), "password")
                val alias = service.aliases.first { it.name == ecCertificateAlias }
                val signature = mock<Signature> {
                    on { sign() } doReturn "signature".toByteArray()
                }
                val sign = mockStatic(Signature::class.java).use { mockSignature ->
                    mockSignature.`when`<Signature> {
                        Signature.getInstance(
                            SigningService.Hash.SHA256.ecName,
                            ecSignatureProvider
                        )
                    }.doReturn(
                        signature
                    )

                    alias.sign(hash, data)
                }

                assertThat(sign).isEqualTo("signature".toByteArray())
            }

            @Test
            fun `ec sign sends the correct data`() {
                val data = "data".toByteArray()
                val hash = SigningService.Hash.SHA512
                val service = JksSigningService("hello".toByteArray(), "password")
                val alias = service.aliases.first { it.name == ecCertificateAlias }
                val signature = mock<Signature> {
                    on { sign() } doReturn "signature".toByteArray()
                }
                mockStatic(Signature::class.java).use { mockSignature ->
                    mockSignature.`when`<Signature> {
                        Signature.getInstance(
                            SigningService.Hash.SHA512.ecName,
                            ecSignatureProvider
                        )
                    }.doReturn(
                        signature
                    )

                    alias.sign(hash, data)
                }

                verify(signature).initSign(privateKey)
                verify(signature, never()).setParameter(any())
                verify(signature).update(data)
            }

            @Test
            fun `ec sign will fail if it cant find the original service provider`() {
                mockSecurity.`when`<Array<Provider>> {
                    Security.getProviders()
                }.doReturn(
                    arrayOf(myProvider, originalProvider, rsaSignatureProvider)
                )
                val data = "data".toByteArray()
                val hash = SigningService.Hash.SHA384
                val service = JksSigningService("hello".toByteArray(), "password")
                val alias = service.aliases.first { it.name == ecCertificateAlias }
                val signature = mock<Signature>()
                mockStatic(Signature::class.java).use { mockSignature ->
                    mockSignature.`when`<Signature> {
                        Signature.getInstance(
                            hash.ecName,
                            ecSignatureProvider
                        )
                    }.doReturn(
                        signature
                    )

                    assertThrows<SecurityException> {
                        alias.sign(hash, data)
                    }
                }
            }
        }
    }
}
