package net.corda.p2p.fake.ca

import net.corda.crypto.test.certificates.generation.toPem
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import java.io.File

@Command(
    name = "create-certificate",
    aliases = ["certificate", "cert"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    description = ["Create TLS certificates"],
    showAtFileInUsageHelp = true,
    subcommandsRepeatable = true,
)
class CreateCertificate : Runnable {
    @Parameters(
        description = ["The DNS names that will be set as subject alternative names in the certificate."],
        arity = "1..*",
    )
    private lateinit var dnsNames: List<String>

    @Option(
        names = ["-n", "--name"],
        description = ["The filename of the certificate (defaults to the first host name)"]
    )
    private var name: String? = null

    @ParentCommand
    private lateinit var ca: Ca

    override fun run() {
        if (!ca.home.isDirectory) {
            throw FakeCaException("Please create the CA before attempting to create certificates")
        }
        val actualName = name ?: dnsNames.first()
        if (actualName == "ca") {
            throw FakeCaException("The name 'ca' can not be used")
        }
        val dir = File(ca.home, actualName).also {
            it.mkdirs()
        }

        val keysAndCertificate = ca.authority.generateKeyAndCertificate(dnsNames)
        // Save the authority because the serial number had changed.
        ca.authority.save()

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
