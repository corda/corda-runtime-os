package net.corda.crypto.test.certificates.generation

/**
 * A file system based certificate authority
 */
interface FileSystemCertificatesAuthority : LocalCertificatesAuthority {

    /**
     * Call to save the detailed to the local file system.
     */
    fun save()
}
