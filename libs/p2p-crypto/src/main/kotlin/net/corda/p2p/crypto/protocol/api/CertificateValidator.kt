package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.io.ByteArrayInputStream
import java.lang.IllegalArgumentException
import java.security.KeyStore
import java.security.cert.CertPath
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.LinkedList

class CertificateValidator(
    private val revocationCheckMode: RevocationCheckMode,
    private val trustStore: KeyStore,
    private val certPathValidator: CertPathValidator = CertPathValidator.getInstance(certificateAlgorithm),
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance(certificateFactoryType),
) {

    private companion object {
        const val certificateAlgorithm = "PKIX"
        const val certificateFactoryType = "X.509"
        const val digital_signature_bit = 0
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
        val x500PrincipalFromCert = (certificateChain.certificates.firstOrNull() as? X509Certificate)?.subjectX500Principal ?:
            throw InvalidPeerCertificate("Session certificate is not an X509 certificate.")
        val x500NameFromCert = try {
            MemberX500Name.build(x500PrincipalFromCert)
        } catch (exception: IllegalArgumentException) {
            throw InvalidPeerCertificate(
                "X500 principle in session certificate ($x500PrincipalFromCert) is not a valid corda X500 Name."
            )
        }
        if (x500NameFromCert != expectedX500Name) {
            throw InvalidPeerCertificate(
                "X500 principle in session certificate ($x500NameFromCert) is different from expected ($expectedX500Name)."
            )
        }

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
            throw InvalidPeerCertificate(exception.message)
        }
        checkAllCertsInChainDigitalSignature(certificateChain)
    }

    private fun checkAllCertsInChainDigitalSignature(certificateChain: CertPath) {
        certificateChain.certificates.forEachIndexed { index, certificate ->
            val x509Cert = (certificate as? X509Certificate)
                ?: throw InvalidPeerCertificate("Certificate $index in chain is not a valid X509 certificate.")
            if (!x509Cert.keyUsage[digital_signature_bit]) {
                throw InvalidPeerCertificate("Certificate $index in chain is not a digital signature.")
            }
        }
    }

    object AllowAllRevocationChecker : PKIXRevocationChecker() {

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

}