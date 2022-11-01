package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.AllowAllRevocationChecker
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.io.ByteArrayInputStream
import java.lang.IllegalArgumentException
import java.security.KeyStore
import java.security.cert.CertPath
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate

class CertificateValidator(
    private val revocationCheckMode: RevocationCheckMode,
    private val trustStore: KeyStore,
    private val certPathValidator: CertPathValidator = CertPathValidator.getInstance(certificateAlgorithm),
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance(certificateFactoryType),
) {

    private companion object {
        const val certificateAlgorithm = "PKIX"
        const val certificateFactoryType = "X.509"
        const val digitalSignatureBit = 0
        val logger = contextLogger()
    }

    @Suppress("ThrowsCount")
    fun validate(cert: List<String>, expectedX500Name: MemberX500Name) {
        val certificateChain = certificateFactory.generateCertPath(cert.map { pemCertificate ->
            ByteArrayInputStream(pemCertificate.toByteArray()).use {
                certificateFactory.generateCertificate(it)
            }
        })
        //By convention, the certificates in a CertPath object of type X.509 are ordered starting with the target certificate
        //and ending with a certificate issued by the trust anchor. So we check the subjectX500Principal of the first certificate
        //matches the x500Name of the peer's identity.
        val x509RootCert = (certificateChain.certificates.firstOrNull() as? X509Certificate) ?:
            throw InvalidPeerCertificate("Root session certificate is not an X509 certificate.")

        validateX500NameMatches(x509RootCert, expectedX500Name)
        validateKeyUsage(x509RootCert)
        validateCertPath(certificateChain)
    }

    private fun validateX500NameMatches(certificate: X509Certificate, expectedX500Name: MemberX500Name) {
        val x500PrincipalFromCert = certificate.subjectX500Principal
        val x500NameFromCert = try {
            MemberX500Name.build(x500PrincipalFromCert)
        } catch (exception: IllegalArgumentException) {
            throw InvalidPeerCertificate(
                "X500 principle in root session certificate ($x500PrincipalFromCert) is not a valid corda X500 Name.",
                certificate
            )
        }
        if (x500NameFromCert != expectedX500Name) {
            throw InvalidPeerCertificate(
                "X500 principle in root session certificate ($x500NameFromCert) is different from expected ($expectedX500Name).",
                certificate
            )
        }
    }

    private fun validateKeyUsage(certificate: X509Certificate) {
        if (!certificate.keyUsage[digitalSignatureBit]) {
            throw InvalidPeerCertificate("The key usages extension of the root session certificate does not contain " +
                "'Digital Signature', as expected.", certificate
            )
        }
    }

    private fun validateCertPath(certificateChain: CertPath) {
        val pkixParams = PKIXBuilderParameters(trustStore, X509CertSelector())
        val revocationChecker = when (revocationCheckMode) {
            RevocationCheckMode.OFF -> AllowAllRevocationChecker
            RevocationCheckMode.SOFT_FAIL, RevocationCheckMode.HARD_FAIL -> {
                val certPathBuilder = CertPathBuilder.getInstance(certificateAlgorithm)
                val pkixRevocationChecker = certPathBuilder.revocationChecker as PKIXRevocationChecker
                // We only set SOFT_FAIL as a checker option if specified. Everything else is left as default, which means
                // OCSP is used if possible, CRL as a fallback
                if (revocationCheckMode == RevocationCheckMode.SOFT_FAIL) {
                    pkixRevocationChecker.options = setOf(PKIXRevocationChecker.Option.SOFT_FAIL)
                }
                pkixRevocationChecker
            }
        }
        pkixParams.addCertPathChecker(revocationChecker)

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