package net.corda.crypto.test.certificates.generation

/**
 * A file system based certificate authority
 */
interface FileSystemCertificatesAuthority : CertificateAuthority {

    /**
     * Call to save the details to the local file system for later re-use of the same certificate authority.
     */
    fun save()
}
