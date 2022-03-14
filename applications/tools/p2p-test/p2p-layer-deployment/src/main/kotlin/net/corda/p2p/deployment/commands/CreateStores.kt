package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.getAndCheckEnv
import net.corda.crypto.test.certificates.generation.StubCertificatesAuthority
import net.corda.crypto.test.certificates.generation.StubCertificatesAuthority.Companion.PASSWORD
import net.corda.crypto.test.certificates.generation.StubCertificatesAuthority.Companion.toPem
import net.corda.v5.cipher.suite.schemes.SignatureSchemeTemplate
import java.io.File

internal class CreateStores(
    val trustStoreFile: File,
    val tlsCertificates: File,
    val sslStoreFile: File,
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
        signatureSchemeTemplate: SignatureSchemeTemplate,
    ) {
        val authority = if (trustStoreLocation != null) {
            StubCertificatesAuthority.createLocalAuthority(signatureSchemeTemplate, trustStoreLocation)
        } else {
            StubCertificatesAuthority.createTinyCertAuthority(
                apiKey, passPhrase, email, authorityName
            )
        }
        authority.use { certificatesAuthority ->
            trustStoreFile.writeText(certificatesAuthority.caCertificate.toPem())
            val keyStore = certificatesAuthority.generateKeyAndCertificate(hosts)
            sslStoreFile.outputStream().use {
                keyStore.toKeyStore().store(it, PASSWORD.toCharArray())
            }
            tlsCertificates.writeText(keyStore.certificatePem())
        }
    }
}
