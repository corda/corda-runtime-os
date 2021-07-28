@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package net.corda.p2p.gateway.messaging.http

import net.corda.nodeapi.internal.crypto.x509
import net.corda.p2p.NetworkType
import net.corda.v5.application.identity.CordaX500Name
import org.slf4j.LoggerFactory
import java.security.KeyStore
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SNIServerName
import javax.net.ssl.StandardConstants
import sun.security.util.HostnameChecker
import sun.security.util.HostnameChecker.TYPE_TLS
import java.lang.NumberFormatException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class HostnameMatcher(private val keyStore: KeyStore) : SNIMatcher(0) {

    private val logger = LoggerFactory.getLogger(HostnameMatcher::class.java)

    var matchedAlias: String?  = null
        private set
    var matchedServerName: String? = null
        private set

    /**
     * Verifies the keystore entries against the provided *serverName*. The method will verify C4 and C5 SNI values.
     * For C4, the value is calculated as a hash of the x500Name. For C5, the CN component as well as the alt subject names
     * are compared with the *serverName*
     */
    override fun matches(serverName: SNIServerName): Boolean {
        val serverNameString = (serverName as SNIHostName).asciiName
        if (serverName.type == StandardConstants.SNI_HOST_NAME) {
            keyStore.aliases().toList().forEach { alias ->
                val certificate = keyStore.getCertificate(alias).x509
                val cordaX500Name = CordaX500Name.build(certificate.subjectX500Principal)
                val c4SniValue = SniCalculator.calculateSni(cordaX500Name.toString(), NetworkType.CORDA_4, "")
                val c5Check = try {
                    HostnameChecker.getInstance(TYPE_TLS).match(serverNameString, certificate)
                    true
                } catch (e: CertificateException) {
                    false
                }

                if (serverNameString == c4SniValue || cordaX500Name.commonName == serverNameString || c5Check) {
                    matchedAlias = alias
                    matchedServerName = serverName.asciiName
                    return true
                }
            }
        }

        val requestedSNIValue = "hostname = $serverNameString"
        logger.warn("Could not find a certificate matching the requested SNI value [$requestedSNIValue]")
        return false
    }

    private fun match(name: String, certificate: X509Certificate): Boolean {
        if (isIpAddress(name)) {
            matchIp(name, certificate)
        } else {
            matchDNS(name, certificate)
        }
    }

    private fun isIpAddress(str: String): Boolean {
        return isIpV4Address(str) || isIpV6Address(str)
    }

    private fun isIpV4Address(str: String): Boolean {
        val octets = str.split(".")
        if (octets.size != 4)
            return false
        try {
            octets.forEach {
                val intValue = it.toInt()
                if (intValue < 1 || intValue > 255)
                    return false
            }
        } catch (e: NumberFormatException) {
            return false
        }
        return true
    }

    private fun isIpV6Address(str: String): Boolean {
        // A lot more tricky to do
        return false
    }

    private fun matchIp(name: String, certificate: X509Certificate): Boolean {
        val names = certificate.subjectAlternativeNames
        if (names.isEmpty()) {
            logger.debug("No subject alternative names found in the certificate")
            return false
        }

        names.forEach {
            if (7 == it[0]) {
                val address = it[1
            }
        }
    }

    private fun matchDNS(name: String, certificate: X509Certificate): Boolean {

    }
}