package net.corda.p2p.fake.ca

import net.corda.crypto.test.certificates.generation.toPem
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import java.io.File

@Command(
    name = "sign-certificate-request",
    aliases = ["sig", "csr", "sign-csr"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    description = ["Sign Certificate Signing Request"],
    showAtFileInUsageHelp = true,
    subcommandsRepeatable = true,
)
class SignCertificate : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The filename of the certificate (defaults to the CSR file name)."]
    )
    private var name: String? = null

    @Parameters(
        description = ["The filename of the request (in PEM format)"],
    )
    private lateinit var csrFile: File

    @ParentCommand
    private lateinit var ca: Ca

    @Suppress("ComplexMethod")
    override fun run() {
        if (!ca.home.isDirectory) {
            throw FakeCaException("Please create the CA before attempting to sign certificate requests.")
        }
        if (!csrFile.canRead()) {
            throw FakeCaException("Can not access $csrFile")
        }
        val actualName = name ?: csrFile.nameWithoutExtension
        if (actualName == Ca.CA_NAME) {
            throw FakeCaException("The name '${Ca.CA_NAME}' can not be used")
        }
        val dir = File(ca.home, actualName).also {
            it.mkdirs()
        }

        val request = csrFile.reader().use { reader ->
            PEMParser(reader).use { parser ->
                parser.readObject()
            }
        }
        if (request !is PKCS10CertificationRequest) {
            throw FakeCaException("The file '$csrFile' has no certificate signing request")
        }

        val certificate = ca.authority.signCsr(request)

        // Save the authority because the serial number had changed.
        ca.authority.save()

        File(dir, "certificate.pem").also {
            it.writeText(certificate.toPem())
            println("Wrote certificate to $it")
        }
    }
}
