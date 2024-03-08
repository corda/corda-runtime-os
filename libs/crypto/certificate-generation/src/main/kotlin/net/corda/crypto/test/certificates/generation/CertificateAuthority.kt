package net.corda.crypto.test.certificates.generation

import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.PublicKey
import java.security.cert.Certificate

/**
 * A certificate authority that can be used for testing.
 *
 * @see [CertificateAuthorityFactory], [LocalCertificatesAuthority], and [FileSystemCertificatesAuthority]
 */
interface CertificateAuthority {
    companion object {
        /**
         * The password that is used when creating a key store.
         */
        const val PASSWORD = "password"
    }
    /**
     * Return the CA certificate.
     */
    val caCertificate: Certificate

    /**
     * Generate a keypair and a certificate with a single host name.
     *
     * @param host - the name of the host.
     * @return A private key and certificates.
     */
    fun generateKeyAndCertificates(host: String) = generateKeyAndCertificates(listOf(host))

    /**
     * Generate a keypair and a certificate with a list of hosts names.
     *
     * @param hosts - the list of hosts.
     * @return A private key and certificates.
     */
    fun generateKeyAndCertificates(hosts: Collection<String>): PrivateKeyWithCertificate

    /**
     * Generate a certificate from a [publicKey] with a list of hosts names.
     *
     * @param publicKey -
     * @param hosts - the list of hosts.
     * @return The generated certificate.
     */
    fun generateCertificates(hosts: Collection<String>, publicKey: PublicKey): Collection<Certificate>

    /**
     * Sign a certificate from a certificate signing request.
     *
     * @param csr - The request.
     * @return The sign certificate in a chain.
     */
    fun signCsr(csr: PKCS10CertificationRequest): Collection<Certificate>

    /**
     * Creates an intermediate CA.
     */
    fun createIntermediateCertificateAuthority() : CertificateAuthority
}
