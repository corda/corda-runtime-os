package net.corda.p2p.gateway.messaging.http

import io.netty.handler.ssl.SslHandler
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.LinkedList
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager
import net.corda.v5.base.util.toHex
import org.bouncycastle.asn1.x500.X500Name

const val HANDSHAKE_TIMEOUT = 10000L
const val TLS_VERSION = "TLSv1.3"
val CIPHER_SUITES = arrayOf("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384")


fun createClientSslHandler(targetServerName: String,
                           target: URI,
                           targetX500Name: X500Name?,
                           trustManagerFactory: TrustManagerFactory): SslHandler {
    val sslContext = SSLContext.getInstance("TLS")
    val trustManagers = trustManagerFactory.trustManagers.filterIsInstance(X509ExtendedTrustManager::class.java)
        .map { IdentityCheckingTrustManager(it, targetX500Name) }.toTypedArray()
    sslContext.init(null, trustManagers, SecureRandom()) //May need to use secure random from crypto-api module

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

fun createServerSslHandler(keyStore: KeyStoreWithPassword): SslHandler {

    val sslContext = SSLContext.getInstance("TLS")

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).also {
        it.init(keyStore.keyStore, keyStore.password.toCharArray())
    }
    /**
     * As per the JavaDoc of SSLContext:
     * Only the first instance of a particular key and/or trust manager implementation type in the array is used.
     * (For example, only the first javax.net.ssl.X509KeyManager in the array will be used.)
     * We shall initialise the SSLContext with an SNI enabled key manager instead
     */
    val keyManagers = keyManagerFactory.keyManagers
    // May need to use secure random from crypto-api module
    sslContext.init(arrayOf(SNIKeyManager(keyManagers.first() as X509ExtendedKeyManager)), null, SecureRandom())

    val sslEngine = sslContext.createSSLEngine().also {
        it.useClientMode = false
        it.needClientAuth = false
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

fun getCertCheckingParameters(trustStore: KeyStore, revocationConfig: RevocationConfig): ManagerFactoryParameters {
    val pkixParams = PKIXBuilderParameters(trustStore, X509CertSelector())
    val revocationChecker = when (revocationConfig.mode) {
        RevocationConfigMode.OFF -> AllowAllRevocationChecker
        RevocationConfigMode.SOFT_FAIL, RevocationConfigMode.HARD_FAIL -> {
            val certPathBuilder = CertPathBuilder.getInstance("PKIX")
            val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
            // We only set SOFT_FAIL as a checker option if specified. Everything else is left as default, which means
            // OCSP is used if possible, CRL as a fallback
            if (revocationConfig.mode == RevocationConfigMode.SOFT_FAIL) {
                pkixRevocationChecker.options = setOf(PKIXRevocationChecker.Option.SOFT_FAIL)
            }
            pkixRevocationChecker
        }
    }
    pkixParams.addCertPathChecker(revocationChecker)
    return CertPathTrustManagerParameters(pkixParams)
}

fun X509Certificate.distributionPoints() : Set<String> {
    val logger = LoggerFactory.getLogger("net.corda.p2p.gateway.messaging.http.SSLHelper")

    logger.debug("Checking CRLDPs for $subjectX500Principal")

    val crldpExtBytes = getExtensionValue(Extension.cRLDistributionPoints.id)
    if (crldpExtBytes == null) {
        logger.debug("NO CRLDP ext")
        return emptySet()
    }

    val derObjCrlDP = ASN1InputStream(ByteArrayInputStream(crldpExtBytes)).readObject()
    val dosCrlDP = derObjCrlDP as? DEROctetString
    if (dosCrlDP == null) {
        logger.error("Expected to have DEROctetString, actual type: ${derObjCrlDP.javaClass}")
        return emptySet()
    }
    val crldpExtOctetsBytes = dosCrlDP.octets
    val dpObj = ASN1InputStream(ByteArrayInputStream(crldpExtOctetsBytes)).readObject()
    val distPoint = CRLDistPoint.getInstance(dpObj)
    if (distPoint == null) {
        logger.error("Could not instantiate CRLDistPoint, from: $dpObj")
        return emptySet()
    }

    val dpNames = distPoint.distributionPoints.mapNotNull { it.distributionPoint }.filter { it.type == DistributionPointName.FULL_NAME }
    val generalNames = dpNames.flatMap { GeneralNames.getInstance(it.name).names.asList() }
    return generalNames.filter { it.tagNo == GeneralName.uniformResourceIdentifier}.map { DERIA5String.getInstance(it.name).string }.toSet()
}

fun X509Certificate.toBc() = X509CertificateHolder(encoded)

fun Certificate.x509(): X509Certificate = requireNotNull(this as? X509Certificate) { "Not an X.509 certificate: $this" }

fun X509Certificate.distributionPointsToString() : String {
    return with(distributionPoints()) {
        if(isEmpty()) {
            "NO CRLDP ext"
        } else {
            sorted().joinToString()
        }
    }
}

fun certPathToString(certPath: Array<out X509Certificate>?): String {
    if (certPath == null) {
        return "<empty certpath>"
    }
    val certs = certPath.map {
        val bcCert = it.toBc()
        val subject = bcCert.subject.toString()
        val issuer = bcCert.issuer.toString()
        val keyIdentifier = try {
            SubjectKeyIdentifier.getInstance(bcCert.getExtension(Extension.subjectKeyIdentifier).parsedValue).keyIdentifier.toHex()
        } catch (ex: Exception) {
            "null"
        }
        val authorityKeyIdentifier = try {
            AuthorityKeyIdentifier.getInstance(bcCert.getExtension(Extension.authorityKeyIdentifier).parsedValue).keyIdentifier.toHex()
        } catch (ex: Exception) {
            "null"
        }
        "  $subject[$keyIdentifier] issued by $issuer[$authorityKeyIdentifier] [${it.distributionPointsToString()}]"
    }
    return certs.joinToString("\r\n")
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