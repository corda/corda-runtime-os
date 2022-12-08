package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.NetworkType
import org.apache.commons.validator.routines.DomainValidator
import org.apache.commons.validator.routines.InetAddressValidator
import org.slf4j.LoggerFactory
import java.security.KeyStore
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SNIServerName
import javax.net.ssl.StandardConstants
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.GeneralName

class HostnameMatcher(private val keyStore: KeyStore) : SNIMatcher(0) {

    companion object {
        private val logger = LoggerFactory.getLogger(HostnameMatcher::class.java)
        private const val ALTNAME_DNS = GeneralName.dNSName
        private const val ALTNAME_IP = GeneralName.iPAddress
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
        if (serverName.type != StandardConstants.SNI_HOST_NAME) {
            logger.warn("Invalid server name type: ${serverName.type}. Supported types: SNIHostName(0)")
        }

        keyStore.aliases().toList().forEach { alias ->
            val certificate = keyStore.getCertificate(alias).x509()
            if (isC4SNI(serverNameString)) {
                val x500Name = X500Name.getInstance(certificate.subjectX500Principal.encoded)
                val c4SniValue = SniCalculator.calculateSni(x500Name.toString(), NetworkType.CORDA_4, "")
                if (serverNameString == c4SniValue) {
                    return matched(alias, serverName.asciiName)
                }
            } else if (isIpSni(serverNameString)) {
                val ipAddress = serverNameString.removeSuffix(SniCalculator.IP_SNI_SUFFIX)
                if (matchIp(ipAddress, certificate)) {
                    return matched(alias, serverName.asciiName)
                }
            } else if (matchDNS(serverNameString, certificate)){
                return matched(alias, serverName.asciiName)
            }
        }

        val requestedSNIValue = "hostname = $serverNameString"
        logger.warn("Could not find a certificate matching the requested SNI value [$requestedSNIValue]")
        return false
    }

    private fun isC4SNI(serverName: String): Boolean {
        val correctSize = serverName.length == SniCalculator.HASH_TRUNCATION_SIZE + SniCalculator.CLASSIC_CORDA_SNI_SUFFIX.length
        val correctSuffix = serverName.endsWith(SniCalculator.CLASSIC_CORDA_SNI_SUFFIX)
        val validHashedLegalName = !serverName.substringBefore('.')
            .toCharArray()
            .map { Character.digit(it, 16) }
            .contains(-1)

        return correctSize && correctSuffix && validHashedLegalName
    }

    private fun isIpSni(serverName: String): Boolean {
        val correctSuffix = serverName.endsWith(SniCalculator.IP_SNI_SUFFIX)
        val validIp = InetAddressValidator.getInstance().isValid(serverName.removeSuffix(SniCalculator.IP_SNI_SUFFIX))
        return correctSuffix && validIp
    }

    private fun matched(alias: String, serverName: String): Boolean {
        matchedAlias = alias
        matchedServerName = serverName
        return true
    }

    private fun matchIp(ip: String, certificate: X509Certificate): Boolean {
        val names = certificate.subjectAlternativeNames
        if (names.isNullOrEmpty()) {
            logger.debug("No subject alternative names found in the certificate")
            return false
        }
        names.forEach {
            if (ALTNAME_IP == it[0]) {
                val ipAddress = it[1] as String
                if (ip == ipAddress) {
                    return true
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
        if (names.isNullOrEmpty()) {
            logger.debug("No subject alternative names found in the certificate")
            return false
        }
        names.forEach {
            if(ALTNAME_DNS == it[0]) {
                val altName = it[1] as String
                if (matchWithWildcard(hostName.lowercase(), altName.lowercase())) {
                    return true
                }
            }
        }

        // If the SNI doesn't match any of the alternate subject names, we check against the CN component of the main
        val x500Name = X500Name.getInstance(certificate.subjectX500Principal.encoded)
        val attrs = x500Name.rdNs
            .flatMap { it.typesAndValues.asList() }
            .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
            .mapValues { it.value[0].toString() }
        val cn = attrs[BCStyle.CN]
        if (matchWithWildcard(hostName.lowercase(), cn?.lowercase())) {
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

        // Cannot have other groups before the wildcard (www.*.test.net)
        if (name.substring(0, wildcardIndex).contains('.')) {
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
