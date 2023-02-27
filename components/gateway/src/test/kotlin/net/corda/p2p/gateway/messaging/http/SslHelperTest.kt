package net.corda.p2p.gateway.messaging.http

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

class SslHelperTest {
    private val sslParameters = mock<SSLParameters>()
    private val sslEngine = mock<SSLEngine> {
        on { sslParameters } doReturn sslParameters
    }
    private val sslContext = mock<SSLContext> {
        on { createSSLEngine(any(), any()) } doReturn sslEngine
        on { createSSLEngine() } doReturn sslEngine
    }
    private val sslContextMock = mockStatic(SSLContext::class.java).also {
        it.`when`<SSLContext> {
            SSLContext.getInstance(any())
        } doReturn sslContext
    }
    private val keyManager = mock<X509ExtendedKeyManager>()
    private val keyManagerFactory = mock<KeyManagerFactory> {
        on { keyManagers } doReturn arrayOf(keyManager)
    }
    private val mockKeyManagerFactory = mockStatic(KeyManagerFactory::class.java).also {
        it.`when`<String> {
            KeyManagerFactory.getDefaultAlgorithm()
        } doReturn "alg"
        it.`when`<KeyManagerFactory> {
            KeyManagerFactory.getInstance("alg")
        } doReturn keyManagerFactory
    }

    @AfterEach
    fun cleanUp() {
        sslContextMock.close()
        mockKeyManagerFactory.close()
    }

    @Nested
    inner class CreateServerSslHandlerTest {
        private val keyStore = mock<KeyStoreWithPassword> {
            on { keyStore } doReturn mock()
            on { password } doReturn "password"
        }

        @Test
        fun `it initialize the context without a key manager for one way TLS`() {
            createServerSslHandler(keyStore, null)

            verify(sslContext).init(any(), eq(null), any())
        }

        @Test
        fun `it initialize the context with a key manager for mutual TLS`() {
            val mutualTlsTrustManager = mock<X509ExtendedTrustManager>()
            createServerSslHandler(keyStore, mutualTlsTrustManager)

            verify(sslContext).init(any(), eq(arrayOf(mutualTlsTrustManager)), any())
        }

        @Test
        fun `it initialize the key manager`() {
            createServerSslHandler(keyStore, null)

            verify(keyManagerFactory).init(any(), eq("password".toCharArray()))
        }

        @Test
        fun `it initialize the context with SNIKeyManager`() {
            createServerSslHandler(keyStore, null)

            verify(sslContext).init(
                argThat {
                    size == 1 && first() is SNIKeyManager
                },
                anyOrNull(),
                any()
            )
        }

        @Test
        fun `it set the correct engine arguments for one way tls`() {
            createServerSslHandler(keyStore, null)

            verify(sslEngine).useClientMode = false
            verify(sslEngine).needClientAuth = false
            verify(sslEngine).enabledProtocols = arrayOf(TLS_VERSION)
            verify(sslEngine).enabledCipherSuites = CIPHER_SUITES
            verify(sslEngine).enableSessionCreation = true
            verify(sslParameters).sniMatchers = argThat {
                size == 1 && first() is HostnameMatcher
            }
        }

        @Test
        fun `it set the correct engine arguments for mutual`() {
            createServerSslHandler(keyStore, mock())

            verify(sslEngine).useClientMode = false
            verify(sslEngine).needClientAuth = true
            verify(sslEngine).enabledProtocols = arrayOf(TLS_VERSION)
            verify(sslEngine).enabledCipherSuites = CIPHER_SUITES
            verify(sslEngine).enableSessionCreation = true
            verify(sslParameters).sniMatchers = argThat {
                size == 1 && first() is HostnameMatcher
            }
        }

        @Test
        fun `it set the handshake timeout correctly`() {
            val handler = createServerSslHandler(keyStore, null)

            assertThat(handler.handshakeTimeoutMillis).isEqualTo(HANDSHAKE_TIMEOUT)
        }
    }

    @Nested
    inner class CreateClientSslHandlerTest {
        private val trustManager = mock<X509ExtendedTrustManager>()
        private val trustManagerFactory = mock<TrustManagerFactory> {
            on { trustManagers } doReturn arrayOf(trustManager)
        }

        @Test
        fun `it initialize the context without a key manager for one way TLS`() {
            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                null,
            )

            verify(sslContext).init(eq(null), any(), any())
        }

        @Test
        fun `it initialize the KeyManagerFactory with a the key store and password`() {
            val keyStore = mock<KeyStore>()

            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                KeyStoreWithPassword(
                    keyStore,
                    "test"
                ),
            )

            verify(keyManagerFactory).init(keyStore, "test".toCharArray())
        }

        @Test
        fun `it initialize the context with a key manager for one way TLS`() {
            val keyStore = mock<KeyStore>()

            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                KeyStoreWithPassword(
                    keyStore,
                    "test"
                ),
            )

            verify(sslContext).init(eq(arrayOf(keyManager)), any(), any())
        }

        @Test
        fun `it creates an IdentityCheckingTrustManager`() {
            val x509TrustManagers = (1..4).map {
                mock<X509ExtendedTrustManager>()
            }
            val otherTrustManagers = (1..3).map {
                mock<TrustManager>()
            }
            whenever(trustManagerFactory.trustManagers).doReturn(
                (x509TrustManagers + otherTrustManagers).toTypedArray()
            )
            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                null,
            )

            verify(sslContext).init(
                anyOrNull(),
                argThat {
                    this.size == x509TrustManagers.size &&
                        this.all {
                            it is IdentityCheckingTrustManager
                        }
                },
                any()
            )
        }

        @Test
        fun `it set the correct engine arguments`() {
            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                null,
            )

            verify(sslEngine).useClientMode = true
            verify(sslEngine).enabledProtocols = arrayOf(TLS_VERSION)
            verify(sslEngine).enabledCipherSuites = CIPHER_SUITES
            verify(sslEngine).enableSessionCreation = true
            verify(sslParameters).serverNames = argThat {
                size == 1 && first() is SNIHostName
            }
            verify(sslEngine).sslParameters = sslParameters
        }

        @Test
        fun `it set the algorithm to HTTPS for Corda 5 network`() {
            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                null,
            )

            verify(sslParameters).endpointIdentificationAlgorithm = "HTTPS"
        }

        @Test
        fun `it will set the algorithm to anything for Corda 4 network`() {
            createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                mock(),
                trustManagerFactory,
                null,
            )

            verify(sslParameters, never()).endpointIdentificationAlgorithm = any()
        }

        @Test
        fun `it create the correct handler`() {
            val handler = createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                null,
            )

            assertThat(handler.engine()).isEqualTo(sslEngine)
        }

        @Test
        fun `it set the handshake timeout correctly`() {
            val handler = createClientSslHandler(
                "www.r3.com",
                URI("https://www.r3.com:8121/test"),
                null,
                trustManagerFactory,
                null,
            )

            assertThat(handler.handshakeTimeoutMillis).isEqualTo(HANDSHAKE_TIMEOUT)
        }
    }
}
