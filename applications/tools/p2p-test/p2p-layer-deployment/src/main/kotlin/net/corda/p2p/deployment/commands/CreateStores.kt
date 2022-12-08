package net.corda.p2p.deployment.commands

import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.FileSystemCertificatesAuthority
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.p2p.deployment.getAndCheckEnv
import java.io.File

internal class CreateStores(
    val trustStoreFile: File,
    val tlsCertificates: File,
    val sslPrivateKeyFile: File,
    val trustStoreLocation: File?,
) {
    companion object {
        const val authorityName = "R3P2pAuthority"
    }

    private val apiKey = getAndCheckEnv("TINYCERT_API_KEY")

    private val passPhrase = getAndCheckEnv("TINYCERT_PASS_PHRASE")

    private val email = getAndCheckEnv("TINYCERT_EMAIL")

    fun create(
        hosts: Collection<String>,
        keySchemeTemplate: KeySchemeTemplate,
    ) {
        val certificatesAuthority = if (trustStoreLocation != null) {
            CertificateAuthorityFactory.createFileSystemLocalAuthority(
                keySchemeTemplate.toFactoryDefinitions(),
                trustStoreLocation
            )
        } else {
            CertificateAuthorityFactory.createTinyCertAuthority(
                apiKey, passPhrase, email, authorityName
            )
        }
        trustStoreFile.writeText(certificatesAuthority.caCertificate.toPem())
        val keyStore = certificatesAuthority.generateKeyAndCertificate(hosts)
        sslPrivateKeyFile.writeText(keyStore.privateKey.toPem())
        tlsCertificates.writeText(keyStore.certificatePem())

        if (certificatesAuthority is AutoCloseable) {
            certificatesAuthority.close()
        }
        if (certificatesAuthority is FileSystemCertificatesAuthority) {
            certificatesAuthority.save()
        }
    }
}
