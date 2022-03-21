package net.corda.crypto.test.certificates.generation

import java.security.cert.Certificate

/**
 * A certificate authority that can be used for testing.
 *
 * @see [CertificateAuthorityFactory], [LocalCertificatesAuthority], [FileSystemCertificatesAuthority] and [CloseableCertificateAuthority]
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
     * @return A private key and certificate.
     */
    fun generateKeyAndCertificate(host: String) = generateKeyAndCertificate(listOf(host))

    /**
     * Generate a keypair and a certificate with a list of hosts names.
     *
     * @param hosts - the list of hosts.
     * @return A private key and certificate.
     */
    fun generateKeyAndCertificate(hosts: Collection<String>): PrivateKeyWithCertificate
}
