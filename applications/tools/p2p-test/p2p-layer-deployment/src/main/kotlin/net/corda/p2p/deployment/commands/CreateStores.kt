package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.getAndCheckEnv
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.stub.certificates.StubCertificatesAuthority
import net.corda.p2p.test.stub.certificates.StubCertificatesAuthority.Companion.toPem
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "create-stores",
    showDefaultValues = true,
    description = ["Create key and trust stores"],
    mixinStandardHelpOptions = true,
)
class CreateStores : Runnable {
    companion object {
        const val authorityName = "R3P2pAuthority"
    }

    @Option(
        names = ["-k", "--tinycert-api-key"],
        description = ["The TinyCert API Key"]
    )
    private var apiKey = getAndCheckEnv("TINYCERT_API_KEY")

    @Option(
        names = ["-p", "--tinycert-passphrase"],
        description = ["The TinyCert Pass phrase"]
    )
    private var passPhrase = getAndCheckEnv("TINYCERT_PASS_PHRASE")

    @Option(
        names = ["-e", "--tinycert-email"],
        description = ["The TinyCert email"]
    )
    private var email = getAndCheckEnv("TINYCERT_EMAIL")

    @Option(
        names = ["--hosts"],
        description = ["The host names"]
    )
    var hosts = listOf("corda.net", "www.corda.net", "dev.corda.net")

    @Option(
        names = ["-t", "--trust-store"],
        description = ["The trust store file"]
    )
    var trustStoreFile: File? = File("truststore.pem")

    @Option(
        names = ["-c", "--certificate-chain-file"],
        description = ["The TLS certificate chain file"]
    )
    var tlsCertificates: File? = null

    @Option(
        names = ["-s", "--ssl-store"],
        description = ["The SSL store file"]
    )
    var sslStoreFile = File("keystore.jks")

    @Option(
        names = ["-a", "--key-algorithm"],
        description = ["The keys algorithm"]
    )
    var algo = KeyAlgorithm.RSA

    @Option(
        names = ["--local-trust-store-location"],
        description = ["The trust store location (if omitted, will use tiny cert)"]
    )
    var trustStoreLocation: File? = null

    @Option(
        names = ["--key-store-password"],
        description = ["The key store password"]
    )
    private var keyStorePassword = "password"

    override fun run() {
        val authority = if (trustStoreLocation != null) {
            StubCertificatesAuthority.createLocalAuthority(algo, trustStoreLocation)
        } else {
            StubCertificatesAuthority.createRemoteAuthority(
                apiKey, passPhrase, email, authorityName
            )
        }
        authority.use { certificatesAuthority ->
            trustStoreFile?.writeText(certificatesAuthority.caCertificate.toPem())
            val keyStore = certificatesAuthority.prepareKeyStore(hosts)
            sslStoreFile.outputStream().use {
                keyStore.toKeyStore().store(it, keyStorePassword.toCharArray())
            }
            tlsCertificates?.writeText(keyStore.certificatePem())
        }
    }
}
