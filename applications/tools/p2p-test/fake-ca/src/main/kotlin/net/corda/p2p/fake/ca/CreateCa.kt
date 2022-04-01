package net.corda.p2p.fake.ca

import net.corda.crypto.test.certificates.generation.toPem
import picocli.CommandLine.Command
import picocli.CommandLine.ParentCommand
import java.io.File

@Command(
    name = "create-ca",
    aliases = ["ca"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    description = ["Create TLS certificates authority"],
    showAtFileInUsageHelp = true,
    subcommandsRepeatable = true,
)
class CreateCa : Runnable {
    @ParentCommand
    private lateinit var ca: Ca

    override fun run() {
        ca.authority.save()
        File(ca.home, "ca/root-certificate.pem").also {
            if (!it.exists()) {
                it.writeText(ca.authority.caCertificate.toPem())
                println("Wrote CA root certificate to $it")
            }
        }
    }
}
