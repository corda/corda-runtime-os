package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.AllowAllRevocationChecker
import net.corda.crypto.utils.KeyStoreWithPem
import net.corda.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.p2p.gateway.certificates.RevocationCheckStatus
import net.corda.p2p.gateway.certificates.RevocationMode
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.io.ByteArrayInputStream
import java.lang.IllegalArgumentException
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate

class CertificateValidator(
    private val revocationCheckMode: RevocationCheckMode,
    private val trustStore: KeyStoreWithPem,
    private val checkRevocation: (RevocationCheckRequest) -> RevocationCheckStatus,
    private val certPathValidator: CertPathValidator = CertPathValidator.getInstance(certificateAlgorithm),
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance(certificateFactoryType),
) {

    private companion object {
        const val certificateAlgorithm = "PKIX"
        const val certificateFactoryType = "X.509"
        const val digitalSignatureBit = 0
        val logger = contextLogger()
    }

    fun validate(pemCertificateChain: List<String>, expectedX500Name: MemberX500Name) {
        val certificateChain = certificateFactory.generateCertPath(pemCertificateChain.map { pemCertificate ->
            ByteArrayInputStream(pemCertificate.toByteArray()).use {
                certificateFactory.generateCertificate(it)
            }
        })
        //By convention, the certificates in a CertPath object of type X.509 are ordered starting with the target certificate
        //and ending with a certificate issued by the trust anchor. So we check the subjectX500Principal of the first certificate
        //matches the x500Name of the peer's identity.
        val x509LeafCert = (certificateChain.certificates.firstOrNull() as? X509Certificate) ?:
            throw InvalidPeerCertificate("Leaf session certificate is not an X509 certificate.")

        validateX500NameMatches(x509LeafCert, expectedX500Name)
        validateKeyUsage(x509LeafCert)
        validateCertPath(certificateChain)
        validateRevocation(certificateChain, pemCertificateChain, trustStore.pemKeyStore)
    }

    private fun validateX500NameMatches(certificate: X509Certificate, expectedX500Name: MemberX500Name) {
        val x500PrincipalFromCert = certificate.subjectX500Principal
        val x500NameFromCert = try {
            MemberX500Name.build(x500PrincipalFromCert)
        } catch (exception: IllegalArgumentException) {
            throw InvalidPeerCertificate(
                "X500 principal in leaf session certificate ($x500PrincipalFromCert) is not a valid corda X500 Name.",
                certificate
            )
        }
        if (x500NameFromCert != expectedX500Name) {
            throw InvalidPeerCertificate(
                "X500 principal in leaf session certificate ($x500NameFromCert) is different from expected ($expectedX500Name).",
                certificate
            )
        }
    }

    private fun validateKeyUsage(certificate: X509Certificate) {
        if (!certificate.keyUsage[digitalSignatureBit]) {
            throw InvalidPeerCertificate("The key usages extension of the session certificate does not contain " +
                "'Digital Signature', as expected.", certificate
            )
        }
    }

    private fun validateRevocation(certificateChain: CertPath, pemCertificates: List<String>, trustStore: List<String>) {
        val revocationMode = when (revocationCheckMode) {
            RevocationCheckMode.OFF -> return //No check to do
            RevocationCheckMode.HARD_FAIL -> RevocationMode.HARD_FAIL
            RevocationCheckMode.SOFT_FAIL -> RevocationMode.SOFT_FAIL
        }
        val revocationStatus = checkRevocation(RevocationCheckRequest(pemCertificates, trustStore, revocationMode))
        if (revocationStatus == RevocationCheckStatus.REVOKED) {
            throw InvalidPeerCertificate("The certificate failed validation: the certificate was revoked.", certificateChain.toX509())
        }
    }

    private fun validateCertPath(certificateChain: CertPath) {
        val pkixParams = PKIXBuilderParameters(trustStore.keyStore, X509CertSelector())
        pkixParams.addCertPathChecker(AllowAllRevocationChecker)
        try {
            certPathValidator.validate(certificateChain, pkixParams)
        } catch (exception: CertPathValidatorException) {
            throw InvalidPeerCertificate(exception.message, certificateChain.toX509())
        }
    }

    private fun CertPath.toX509(): Array<X509Certificate?> {
        return this.certificates.map { it as? X509Certificate }.toTypedArray()
    }

}