package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.PemCertificate
import net.corda.data.p2p.gateway.certificates.RevocationMode

interface CertificateValidatorFactory {
    fun create(
        revocationCheckMode: RevocationMode?,
        pemTrustStore: List<PemCertificate>,
        checkRevocation: RevocationChecker,
    ): CertificateValidator

    object Default : CertificateValidatorFactory {
        override fun create(
            revocationCheckMode: RevocationMode?,
            pemTrustStore: List<PemCertificate>,
            checkRevocation: RevocationChecker,
        ): CertificateValidator {
            return CertificateValidator(revocationCheckMode, pemTrustStore, checkRevocation)
        }

    }
}