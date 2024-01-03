package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.PemCertificate
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.data.p2p.crypto.protocol.CheckCertificate as AvroCheckCertificate

/**
 * How should the authentication protocol check the certificates sent as a part of authentication protocol.
 */
sealed class CertificateCheckMode {
    abstract fun toAvro(): AvroCheckCertificate?

    companion object {
        fun AvroCheckCertificate?.toCorda(
            checkRevocation: CheckRevocation,
        ): CertificateCheckMode {
            return if (this == null) {
                NoCertificate
            } else {
                CheckCertificate(
                    truststore = this.truststore,
                    revocationCheckMode = this.revocationCheckMode,
                    checkRevocation,
                )
            }
        }
    }

    /**
     * [NoCertificate]: Assumes no certificate is sent as a part of the authentication protocol.
     */
    object NoCertificate : CertificateCheckMode() {
        override fun toAvro() = null
    }

    /**
     * [CheckCertificate]: Checks the certificate sent as a part of the authentication protocol. Validates that certificate is signed by
     * [CheckCertificate.truststore] and has the expected X500Name. Depending on [RevocationCheckMode] also checks if the certificate has
     * been revoked.
     */
    data class CheckCertificate(
        val truststore: List<PemCertificate>,
        val revocationCheckMode: RevocationCheckMode,
        val revocationChecker: CheckRevocation,
    ) : CertificateCheckMode() {
        override fun toAvro(): AvroCheckCertificate {
            return AvroCheckCertificate(
                truststore,
                revocationCheckMode,
            )
        }
    }
}
