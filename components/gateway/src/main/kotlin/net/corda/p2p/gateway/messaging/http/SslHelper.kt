package net.corda.p2p.gateway.messaging.http

import io.netty.handler.ssl.SslHandler
import net.corda.nodeapi.internal.protonwrapper.netty.LoggingTrustManagerWrapper
import net.corda.nodeapi.internal.protonwrapper.netty.RevocationConfig
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.base.util.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.util.LinkedList
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

const val HANDSHAKE_TIMEOUT = 10000L
const val TLS_VERSION = "TLSv1.3"
val CIPHER_SUITES = arrayOf("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")


fun createClientSslHandler(targetServerName: String,
                           target: NetworkHostAndPort,
                           trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java)
        .map { LoggingTrustManagerWrapper(it) }.toTypedArray()
    sslContext.init(null, trustManagers, SecureRandom()) //May need to use secure random from crypto-api module

    val sslEngine = sslContext.createSSLEngine(target.host, target.port).also {
        it.useClientMode = true
        it.enabledProtocols = arrayOf(TLS_VERSION)
        it.enabledCipherSuites = CIPHER_SUITES
        it.enableSessionCreation = true
        val sslParameters = it.sslParameters
        sslParameters.serverNames = listOf(SNIHostName(targetServerName))
        it.sslParameters = sslParameters
    }
    val sslHandler = SslHandler(sslEngine)
    sslHandler.handshakeTimeoutMillis = HANDSHAKE_TIMEOUT
    return sslHandler
}

fun createServerSslHandler(keyStore: KeyStore,
                           keyManagerFactory: KeyManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")

    /**
     * As per the JavaDoc of SSLContext:
     * Only the first instance of a particular key and/or trust manager implementation type in the array is used.
     * (For example, only the first javax.net.ssl.X509KeyManager in the array will be used.)
     * We shall initialise the SSLContext with an SNI enabled key manager instead
     */
    val keyManagers = keyManagerFactory.keyManagers
    sslContext.init(arrayOf(SNIKeyManager(keyManagers.first() as X509ExtendedKeyManager)), null, SecureRandom()) //May need to use secure random from crypto-api module

    val sslEngine = sslContext.createSSLEngine().also {
        it.useClientMode = false
        it.needClientAuth = false
        it.enabledProtocols = arrayOf(TLS_VERSION)
        it.enabledCipherSuites = CIPHER_SUITES
        it.enableSessionCreation = true
        val sslParameters = it.sslParameters
        sslParameters.sniMatchers = listOf(HostnameMatcher(keyStore))
        it.sslParameters = sslParameters
    }
    val sslHandler = SslHandler(sslEngine)
    sslHandler.handshakeTimeoutMillis = HANDSHAKE_TIMEOUT
    return sslHandler
}

/**
 * Extension to convert Corda names into String representing SNI values to be used for TLS handshakes
 */
fun CordaX500Name.toSNI() = "temp"

fun getCertCheckingParameters(trustStore: KeyStore, revocationConfig: RevocationConfig): ManagerFactoryParameters {
    val pkixParams = PKIXBuilderParameters(trustStore, X509CertSelector())
    val revocationChecker = when (revocationConfig.mode) {
        RevocationConfig.Mode.OFF -> AllowAllRevocationChecker
        RevocationConfig.Mode.EXTERNAL_SOURCE -> {
            // We need to figure out if this is still needed. Likely not as the GW should never be in the DMZ, so
            // outbound connections are allowed
//            require(revocationConfig.externalCrlSource != null) { "externalCrlSource must not be null" }
//            ExternalSourceRevocationChecker(revocationConfig.externalCrlSource!!) { Date() }
            AllowAllRevocationChecker
        }
        else -> {
            val certPathBuilder = CertPathBuilder.getInstance("PKIX")
            val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
            // We only set SOFT_FAIL as a checker option if specified. Everything else is left as default, which means
            // OCSP is used if possible, CRL as a fallback
            if (revocationConfig.mode == RevocationConfig.Mode.SOFT_FAIL) {
                pkixRevocationChecker.options = setOf(PKIXRevocationChecker.Option.SOFT_FAIL)
            }
            pkixRevocationChecker
        }
    }
    pkixParams.addCertPathChecker(revocationChecker)
    return CertPathTrustManagerParameters(pkixParams)
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