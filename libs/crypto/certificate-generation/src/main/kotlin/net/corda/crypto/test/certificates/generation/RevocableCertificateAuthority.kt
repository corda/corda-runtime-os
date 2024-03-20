package net.corda.crypto.test.certificates.generation

import java.security.cert.Certificate

/**
 * A certificate authority that support certificate revocation.
 *
 * Please note that it creates an HTTP server that should be stopped (using the close method).
 */
interface RevocableCertificateAuthority: CloseableCertificateAuthority {
    /**
     * revoke the certificate. After that, the revocation check should fail.
     */
    fun revoke(certificate: Certificate)

    /**
     * un-revoke the certificate. After that, the revocation check should pass again.
     */
    fun reintroduce(certificate: Certificate)
}