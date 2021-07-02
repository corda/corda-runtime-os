package net.corda.p2p.gateway.messaging.http

import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.x509
import net.corda.v5.application.identity.CordaX500Name
import org.slf4j.LoggerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SNIServerName
import javax.net.ssl.StandardConstants

class HostnameMatcher(private val keyStore: CertificateStore) : SNIMatcher(0) {

    private val logger = LoggerFactory.getLogger(HostnameMatcher::class.java)

    var matchedAlias: String?  = null
        private set
    var matchedServerName: String? = null
        private set

    override fun matches(serverName: SNIServerName): Boolean {
        if (serverName.type == StandardConstants.SNI_HOST_NAME) {
            keyStore.aliases().forEach { alias ->
                val x500Name = keyStore[alias].x509.subjectX500Principal
                val cordaX500Name = CordaX500Name.build(x500Name)
                // Convert the CordaX500Name into the expected host name and compare
                // E.g. O=Corda B, L=London, C=GB becomes 3c6dd991936308edb210555103ffc1bb.corda.net
                if ((serverName as SNIHostName).asciiName == cordaX500Name.toSNI()) {
                    matchedAlias = alias
                    matchedServerName = serverName.asciiName
                    return true
                }
            }
        }

        val knownSNIValues = keyStore.aliases().joinToString {
            val x500Name = keyStore[it].x509.subjectX500Principal
            val cordaX500Name = CordaX500Name.build(x500Name)
            "hostname = ${cordaX500Name.toSNI()} alias = $it"
        }
        val requestedSNIValue = "hostname = ${(serverName as SNIHostName).asciiName}"
        logger.warn("The requested SNI value [$requestedSNIValue] does not match any of the following known SNI values [$knownSNIValues]")
        return false
    }
}