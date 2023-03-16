package net.corda.crypto.utils

import net.corda.v5.base.util.EncodingUtils.toHex
import org.bouncycastle.asn1.ASN1IA5String
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.CertPathValidatorException
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509Certificate
import java.util.LinkedList

private const val KEY_STORE_TYPE = "PKCS12"
typealias PemCertificate = String

fun convertToKeyStore(certificateFactory: CertificateFactory, pemCertificates: Collection<PemCertificate>, alias: String): KeyStore? {
    val logger = LoggerFactory.getLogger("net.corda.crypto.utils.CertificateUtils")
    return KeyStore.getInstance(KEY_STORE_TYPE).also { keyStore ->
        keyStore.load(null, null)
        pemCertificates.withIndex().forEach { (index, pemCertificate) ->
            val certificate = ByteArrayInputStream(pemCertificate.toByteArray()).use {
                try {
                    certificateFactory.generateCertificate(it)
                } catch (except: CertificateException) {
                    logger.warn("Could not load certificate: ${except.message}.")
                    return null
                }
            }
            try {
                keyStore.setCertificateEntry("$alias-$index", certificate)
            } catch (except: KeyStoreException) {
                logger.warn("Could not load certificate into keystore: ${except.message}.")
                return null
            }
        }
    }
}

fun X509Certificate.distributionPoints() : Set<String> {
    val logger = LoggerFactory.getLogger("net.corda.crypto.utils.CertificateUtils")

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

    val dpNames = distPoint.distributionPoints.mapNotNull { it.distributionPoint }.filter {
        it.type == DistributionPointName.FULL_NAME
    }
    val generalNames = dpNames.flatMap { GeneralNames.getInstance(it.name).names.asList() }
    return generalNames.filter { it.tagNo == GeneralName.uniformResourceIdentifier}.map {
        ASN1IA5String.getInstance(it.name).string
    }.toSet()
}

fun X509Certificate.toBc() = X509CertificateHolder(encoded)

fun X509Certificate.distributionPointsToString() : String {
    return with(distributionPoints()) {
        if(isEmpty()) {
            "NO CRLDP ext"
        } else {
            sorted().joinToString()
        }
    }
}

fun certPathToString(certPath: Array<out X509Certificate?>?): String {
    if (certPath == null) {
        return "<empty certpath>"
    }
    val certs = certPath.map {
        if (it == null) {
            return@map "not a X509Certificate"
        }
        val bcCert = it.toBc()
        val subject = bcCert.subject.toString()
        val issuer = bcCert.issuer.toString()
        val keyIdentifier = try {
            toHex(SubjectKeyIdentifier.getInstance(bcCert.getExtension(Extension.subjectKeyIdentifier).parsedValue).keyIdentifier)
        } catch (ex: Exception) {
            "null"
        }
        val authorityKeyIdentifier = try {
            toHex(AuthorityKeyIdentifier.getInstance(bcCert.getExtension(Extension.authorityKeyIdentifier).parsedValue).keyIdentifier)
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