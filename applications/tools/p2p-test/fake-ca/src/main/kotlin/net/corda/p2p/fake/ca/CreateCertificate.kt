package net.corda.p2p.fake.ca

import net.corda.crypto.test.certificates.generation.toPem
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import java.io.File

@Command(
    name = "create",
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    description = ["Create TLS certificates"],
    showAtFileInUsageHelp = true,
    subcommandsRepeatable = true,
)
class CreateCertificate : Runnable {
    @Parameters(
        description = ["The names of the hosts"],
        arity = "1..*",
    )
    private lateinit var hosts: List<String>

    @Option(
        names = ["-n", "--name"],
        description = ["The name of the certificate (default to the first host name)"]
    )
    private var name: String? = null

    @ParentCommand
    private lateinit var ca: Ca

    override fun run() {
        val keysAndCertificate = ca.authority.generateKeyAndCertificate(hosts)
        ca.authority.save()
        val actualName = name ?: hosts.first()
        val dir = File(ca.home, actualName).also {
            it.mkdirs()
        }
        File(ca.home, "root-certificate.pem").also {
            if (!it.exists()) {
                it.writeText(ca.authority.caCertificate.toPem())
                println("Wrote CA root certificate to $it")
            }
        }
        File(dir, "certificate.pem").also {
            it.writeText(keysAndCertificate.certificatePem())
            println("Wrote certificate to $it")
        }
        File(dir, "keys.pem").also {
            it.writeText(keysAndCertificate.privateKey.toPem())
            println("Wrote keys to $it")
        }
    }
}
