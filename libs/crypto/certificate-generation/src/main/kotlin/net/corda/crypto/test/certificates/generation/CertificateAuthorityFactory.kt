package net.corda.crypto.test.certificates.generation

import net.corda.v5.cipher.suite.schemes.SignatureSchemeTemplate
import java.io.File
import java.time.Duration

/**
 * A Factory object to create a [CertificateAuthority].
 */
object CertificateAuthorityFactory {

    /**
     * Create a new local authority in memory.
     *
     * @param signatureSchemeTemplate - The signature template (currently, only RSA and EC are supported)
     * @param validDuration - The duration after which the certificate will become invalid
     */
    fun createMemoryAuthority(
        signatureSchemeTemplate: SignatureSchemeTemplate,
        validDuration: Duration = Duration.ofDays(30),
    ): CertificateAuthority {
        return LocalCertificatesAuthority(signatureSchemeTemplate, validDuration, null)
    }

    /**
     * Load any saved authority from the [home] directory, if none exists, generate a new one.
     * Using the [FileSystemCertificatesAuthority.save] will save the authority for future use.
     *
     * @param signatureSchemeTemplate - The signature template (currently, only RSA and EC are supported)
     * @param home - The home directory to save the CA private keys, certificate and serial numbers.
     *      If the specified directory already exists, the factory will attempt to reload the authority from the files in that directory.
     *      Otherwise, a new certificate authority will be created.
     * @param validDuration - The duration after which the certificate will become invalid
     */
    fun createFileSystemLocalAuthority(
        signatureSchemeTemplate: SignatureSchemeTemplate,
        home: File,
        validDuration: Duration = Duration.ofDays(30),
    ): FileSystemCertificatesAuthority {
        return FileSystemCertificatesAuthorityImpl.loadOrGenerate(
            signatureSchemeTemplate, validDuration, home
        )
    }

    /**
     * Create a TinyCert (https://www.tinycert.org/) based authority.
     *
     * @param apiKey - The TinyCert API key.
     * @param passPhrase - The TinyCert pass phrase.
     * @param email - The TinyCert login email address.
     * @param authorityName - The authority name (within TinyCert).
     */
    fun createTinyCertAuthority(
        apiKey: String,
        passPhrase: String,
        email: String,
        authorityName: String,
    ): CloseableCertificateAuthority {
        return TinyCertCertificatesAuthority(
            apiKey,
            passPhrase,
            email,
            authorityName,
        )
    }
}
