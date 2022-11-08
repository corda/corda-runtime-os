package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.KeyStoreWithPem
import net.corda.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.p2p.gateway.certificates.RevocationCheckStatus

/**
 * How should the authentication protocol che   ck the certificates sent as a part of authentication protocol.
 */
sealed class CertificateCheckMode {
    /**
     * [NoCertificate]: Assumes no certificate is sent as a part of the authentication protocol.
     */
    object NoCertificate: CertificateCheckMode()

    /**
     * [CheckCertificate]: Checks the certificate sent as a part of the authentication protocol. Validates that certificate is signed by
     * [CheckCertificate.truststore] and has the expected X500Name. Depending on [RevocationCheckMode] also checks if the certificate has
     * been revoked.
     */
    data class CheckCertificate(
        val truststore: KeyStoreWithPem,
        val ourCertificates: List<String>,
        val revocationCheckMode: RevocationCheckMode,
        val revocationChecker: (request: RevocationCheckRequest) -> RevocationCheckStatus
    ): CertificateCheckMode()
}

enum class RevocationCheckMode {
    OFF, SOFT_FAIL, HARD_FAIL
}