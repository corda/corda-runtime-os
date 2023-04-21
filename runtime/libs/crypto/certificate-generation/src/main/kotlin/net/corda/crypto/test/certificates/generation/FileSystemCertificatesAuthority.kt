package net.corda.crypto.test.certificates.generation

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate

/**
 * A file system based certificate authority
 */
interface FileSystemCertificatesAuthority : CertificateAuthority {

    /**
     * Call to save the details to the local file system for later re-use of the same certificate authority.
     */
    fun save()

    /**
     * Sign a certificate from a certificate signing request.
     *
     * @param csr - The request.
     * @return The sign certificate.
     */
    fun signCsr(csr: PKCS10CertificationRequest): Certificate
}
