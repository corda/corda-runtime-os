package net.corda.p2p.gateway.messaging.http

import io.netty.handler.ssl.SslHandler
import net.corda.crypto.utils.certPathToString
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

const val HANDSHAKE_TIMEOUT = 10000L
const val TLS_VERSION = "TLSv1.3"
val CIPHER_SUITES = arrayOf("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")

fun createClientSslHandler(
    targetServerName: String,
    target: URI,
    targetX500Name: X500Name?,
    trustManagerFactory: TrustManagerFactory,
    clientCertificatesKeyStore: KeyStoreWithPassword?
): SslHandler {
    val keyManagers = clientCertificatesKeyStore?.let { keyStore ->
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).also {
            it.init(
                keyStore.keyStore,
                keyStore.password.toCharArray(),
            )
        }.keyManagers
    }
    val sslContext = SSLContext.getInstance("TLS")
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java)
        .map { IdentityCheckingTrustManager(it, targetX500Name) }.toTypedArray()
    sslContext.init(keyManagers, trustManagers, SecureRandom()) //May need to use secure random from crypto-api module

    val sslEngine = sslContext.createSSLEngine(target.host, target.port).also {
        it.useClientMode = true
        it.enabledProtocols = arrayOf(TLS_VERSION)
        it.enabledCipherSuites = CIPHER_SUITES
        it.enableSessionCreation = true
        val sslParameters = it.sslParameters
        sslParameters.serverNames = listOf(SNIHostName(targetServerName))
        // To enable the JSSE client side checking of server identity, we need to specify and endpoint identification algorithm
        // Supported names are documented here https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html
        if (targetX500Name == null) {
            // Enable identity check for web certificates if no target x500 name is expected
            sslParameters.endpointIdentificationAlgorithm = "HTTPS"
        }
        it.sslParameters = sslParameters
    }
    val sslHandler = SslHandler(sslEngine)
    sslHandler.handshakeTimeoutMillis = HANDSHAKE_TIMEOUT
    return sslHandler
}

fun createServerSslHandler(keyStore: KeyStoreWithPassword, mutualTlsTrustManager: X509ExtendedTrustManager?): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).also {
        it.init(keyStore.keyStore, keyStore.password.toCharArray())
    }

    val trustManagers = mutualTlsTrustManager?.let {
        arrayOf(it)
    }
    /**
     * As per the JavaDoc of SSLContext:
     * Only the first instance of a particular key and/or trust manager implementation type in the array is used.
     * (For example, only the first javax.net.ssl.X509KeyManager in the array will be used.)
     * We shall initialise the SSLContext with an SNI enabled key manager instead
     */
    val keyManagers = keyManagerFactory.keyManagers
    // May need to use secure random from crypto-api module
    sslContext.init(arrayOf(SNIKeyManager(keyManagers.first() as X509ExtendedKeyManager)), trustManagers, SecureRandom())

    val sslEngine = sslContext.createSSLEngine().also {
        it.useClientMode = false
        it.needClientAuth = trustManagers != null
        it.enabledProtocols = arrayOf(TLS_VERSION)
        it.enabledCipherSuites = CIPHER_SUITES
        it.enableSessionCreation = true
        val sslParameters = it.sslParameters
        sslParameters.sniMatchers = listOf(HostnameMatcher(keyStore.keyStore))
        it.sslParameters = sslParameters
    }
    val sslHandler = SslHandler(sslEngine)
    sslHandler.handshakeTimeoutMillis = HANDSHAKE_TIMEOUT
    return sslHandler
}

fun Certificate.x509(): X509Certificate = requireNotNull(this as? X509Certificate) { "Not an X.509 certificate: $this" }

/**
 * Wrapper which adds useful logging about certificates being checked and does client side identity check of presented
 * certificate
 */
class IdentityCheckingTrustManager(private val wrapped: X509ExtendedTrustManager,
                                   private val expectedX500Name: X500Name?) : X509ExtendedTrustManager() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(IdentityCheckingTrustManager::class.java)
    }

    private fun certPathToStringFull(chain: Array<out X509Certificate>?): String {
        if (chain == null) {
            return "<empty certpath>"
        }
        return chain.joinToString(", ") { it.toString() }
    }

    private fun logErrors(chain: Array<out X509Certificate>?, block: () -> Unit) {
        try {
            block()
        } catch (ex: CertificateException) {
            logger.error("Bad certificate identity or path. ${ex.message}\r\n${certPathToStringFull(chain)}")
            throw ex
        }
    }

    /**
     * We're using one way authentication, therefore this trust manager will only be used for server checks.
     * The [checkClientTrusted] methods should never be called. However, we simply call the parent methods.
     */
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        logger.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType, socket) }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        logger.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType, engine) }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        logger.info("Check Client Certpath:\r\n${certPathToString(chain)}")
        logErrors(chain) { wrapped.checkClientTrusted(chain, authType) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        logger.info("Check Server Identity and Certpath: $expectedX500Name\r\n${certPathToString(chain)}")
        logErrors(chain) {
            expectedX500Name?.let {
                checkServerIdentity(chain)
            }
            wrapped.checkServerTrusted(chain, authType, socket)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        logger.info("Check Server Identity and Certpath: $expectedX500Name\r\n${certPathToString(chain)}")
        logErrors(chain) {
            expectedX500Name?.let {
                checkServerIdentity(chain)
            }
            wrapped.checkServerTrusted(chain, authType, engine)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        logger.info("Check Server Identity and Certpath: $expectedX500Name\r\n${certPathToString(chain)}")
        logErrors(chain) {
            expectedX500Name?.let {
                checkServerIdentity(chain)
            }
            wrapped.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = wrapped.acceptedIssuers

    @Throws(CertificateException::class)
    private fun checkServerIdentity(chain: Array<out X509Certificate>?) {
        if (chain.isNullOrEmpty()) {
            throw (CertificateException("Null or empty certificate chain received from server"))
        }

        val receivedX500Name = X500Name.getInstance(chain[0].subjectX500Principal.encoded)
        if (expectedX500Name != receivedX500Name) {
            throw (CertificateException("Certificate name doesn't match. Expected $expectedX500Name but received $receivedX500Name"))
        }

    }
}