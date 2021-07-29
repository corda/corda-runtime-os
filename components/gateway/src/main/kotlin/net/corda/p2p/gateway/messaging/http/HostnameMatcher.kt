package net.corda.p2p.gateway.messaging.http

import net.corda.nodeapi.internal.crypto.x509
import net.corda.p2p.NetworkType
import net.corda.v5.application.identity.CordaX500Name
import org.apache.commons.validator.routines.DomainValidator
import org.apache.commons.validator.routines.InetAddressValidator
import org.slf4j.LoggerFactory
import java.security.KeyStore
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SNIServerName
import javax.net.ssl.StandardConstants
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.cert.X509Certificate

class HostnameMatcher(private val keyStore: KeyStore) : SNIMatcher(1) {

    companion object {
        private val logger = LoggerFactory.getLogger(HostnameMatcher::class.java)

        private const val ALTNAME_DNS = 2
        private const val ALTNAME_IP = 7
    }



    var matchedAlias: String? = null
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
                val c5Check = match(serverNameString, certificate)

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
        return if (InetAddressValidator.getInstance().isValid(name)) {
            matchIp(name, certificate)
        } else {
            matchDNS(name, certificate)
        }
    }

//    private fun isIpAddress(str: String): Boolean {
//        return isIpV4Address(str) || isIpV6Address(str)
//    }
//
//    private fun isIpV4Address(str: String): Boolean {
//        val octets = str.split(".")
//        if (octets.size != 4)
//            return false
//        try {
//            octets.forEach {
//                val intValue = it.toInt()
//                if (intValue < 1 || intValue > 255)
//                    return false
//            }
//        } catch (e: NumberFormatException) {
//            return false
//        }
//        return true
//    }
//
//    private fun isIpV6Address(str: String): Boolean {
//        // Tricky to do
//        return false
//    }

    /**
     * RFC2818: In some cases, the URI is specified as an IP address rather than a
     * hostname. In this case, the iPAddress subjectAltName must be present
     * in the certificate and must exactly match the IP in the URI.
     */
    private fun matchIp(hostIP: String, certificate: X509Certificate): Boolean {
        val names = certificate.subjectAlternativeNames
        if (names.isEmpty()) {
            logger.debug("No subject alternative names found in the certificate")
            return false
        }

        names.forEach {
            if (ALTNAME_IP == it[0]) {
                val altNameAsIP = it[1] as String
                if (hostIP == altNameAsIP) {
                    return true
                } else {
                    // Perhaps it's IPv6, in which case we need to ensure equality in case of abbreviated or long form
                    try {
                        if (InetAddress.getByName(hostIP) == InetAddress.getByName(altNameAsIP)) {
                            return true
                        }
                    } catch (e: UnknownHostException) {
                        logger.debug(e.message)
                    } catch (e: SecurityException) {
                        logger.debug(e.message)
                    }
                }
            }
        }

        return false
    }

    /**
     * RFC2818: If a subjectAltName extension of type dNSName is present, that MUST
     * be used as the identity. Otherwise, the (most specific) Common Name
     * field in the Subject field of the certificate MUST be used. Although
     * the use of the Common Name is existing practice, it is deprecated and
     * Certification Authorities are encouraged to use the dNSName instead.
     *
     * If more than one identity of a given type is present in
     * the certificate (e.g., more than one dNSName name, a match in any one
     * of the set is considered acceptable.) Names may contain the wildcard
     * character * which is considered to match any single domain name
     * component or component fragment. E.g., *.a.com matches foo.a.com but
     * not bar.foo.a.com. f*.com matches foo.com but not bar.com.
     */
    private fun matchDNS(hostName: String, certificate: X509Certificate): Boolean {
        val names = certificate.subjectAlternativeNames
        if (names.isEmpty()) {
            logger.debug("No subject alternative names found in the certificate")
            return false
        }
        var usingDNS = false
        names.forEach {
            if(ALTNAME_DNS == it[0]) {
                usingDNS = true
                val altName = it[1] as String
                if (matchWithWildcard(hostName.toLowerCase(), altName.toLowerCase())) {
                    return true
                }
            }
        }

        if (usingDNS) {
            // If subject alternate names contain DNS names and none match, we don't check the CN
            return false
        }

        val cn = CordaX500Name.build(certificate.subjectX500Principal).commonName
        if (matchWithWildcard(hostName.toLowerCase(), cn?.toLowerCase())) {
            return true
        }

        return false
    }

    internal fun matchWithWildcard(expectedName: String, altName: String?): Boolean {
        altName?.let {
            // Straightforward check
            if (expectedName == altName)
                return true

            // Check if wildcards exist and if they are legal
            if (illegalWildcard(altName)) {
                return false
            }

            val expectedNameSections = expectedName.split(".")
            val altNameSections = altName.split(".")

            // Wildcard can only be used for one group
            if (expectedNameSections.size != altNameSections.size) {
                return false
            }

            expectedNameSections.forEachIndexed { idx, expectedGroup ->
                // Wildcard can be used for entire group or only part of it
                val wildCardIndex = altNameSections[idx].lastIndexOf('*')
                if (wildCardIndex != -1) {
                    // Check if the expected group starts with the characters behind the wildcard
                    // Covers the case of entire group wildcard as well as partial group wildcard (f*.bar)
                    val beforeWildcard = altNameSections[idx].substring(0, wildCardIndex)
                    if (!expectedGroup.startsWith(beforeWildcard)) {
                        return false
                    }
                } else {
                    if (expectedGroup != altNameSections[idx]) {
                        return false
                    }
                }

            }

            return true
        }

        return false
    }

    internal fun illegalWildcard(name: String): Boolean {
        val wildcardIndex = name.lastIndexOf('*')

        // DNS name does not contain wildcard
        if (wildcardIndex == -1) {
            return false
        }

        // DNS name can only have one wildcard
        if (name.substring(0, wildcardIndex).lastIndexOf('*') != -1) {
            return true
        }

        // DNS name cannot be just the wildcard
        if ("*" == name || "*." == name) {
            return true
        }

        // Wildcard character cannot not be the last and needs to be followed by a dot ("*.net")
        if (wildcardIndex == name.length - 1 || name[wildcardIndex + 1] != '.') {
            return true
        }

        val nameAfterWildcard = name.substring(wildcardIndex + 2)
        // Name after wildcard group must be a valid domain
        if (!DomainValidator.getInstance().isValid("filler.$nameAfterWildcard")) {
            return true
        }
        return false
    }
}