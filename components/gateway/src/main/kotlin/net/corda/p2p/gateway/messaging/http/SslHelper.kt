package net.corda.p2p.gateway.messaging.http

import io.netty.handler.ssl.SslHandler
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.SecureRandom
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.PKIXRevocationChecker
import java.util.LinkedList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

fun createClientSslHandler(target: URI,
                           trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java).toTypedArray()
    sslContext.init(null, trustManagers, SecureRandom())

    val sslEngine = sslContext.createSSLEngine(target.host, target.port).also {
        it.useClientMode = true
        it.enabledProtocols = arrayOf("TLSv1.2")
        it.enabledCipherSuites = arrayOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )
        it.enableSessionCreation = true
    }
    val sslHandler = SslHandler(sslEngine)
    sslHandler.handshakeTimeoutMillis = 10000
    return sslHandler
}

fun createServerSslHandler(keyManagerFactory: KeyManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val keyManagers = keyManagerFactory.keyManagers
    sslContext.init(keyManagers, null, SecureRandom())

    val sslEngine = sslContext.createSSLEngine().also {
        it.useClientMode = false
        it.needClientAuth = false
        it.enabledProtocols = arrayOf("TLSv1.2")
        it.enabledCipherSuites = arrayOf(
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
        )
        it.enableSessionCreation = true
    }

    val sslHandler = SslHandler(sslEngine)
    sslHandler.handshakeTimeoutMillis = 10000
    return sslHandler
}

object AllowAllRevocationChecker : PKIXRevocationChecker() {

    private val logger = LoggerFactory.getLogger(AllowAllRevocationChecker::class.java)

    override fun check(cert: Certificate?, unresolvedCritExts: MutableCollection<String>?) {
        logger.debug("Passing certificate check for: $cert")
        // Nothing to do
    }

    override fun isForwardCheckingSupported(): Boolean {
        return true
    }

    override fun getSupportedExtensions(): MutableSet<String>? {
        return null
    }

    override fun init(forward: Boolean) {
        // Nothing to do
    }

    override fun getSoftFailExceptions(): MutableList<CertPathValidatorException> {
        return LinkedList()
    }
}