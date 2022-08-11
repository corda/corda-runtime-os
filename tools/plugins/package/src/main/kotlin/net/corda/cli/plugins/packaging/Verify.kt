package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.libs.packaging.verify.PackageType
import net.corda.libs.packaging.verify.VerifierBuilder
import picocli.CommandLine
import java.io.FileInputStream
import java.io.InputStream
import net.corda.cli.plugins.packaging.signing.CertificateLoader.readCertificates

@CommandLine.Command(
    name = "verify",
    description = ["Verifies a CPK/CPB/CPI."]
)
class Verify : Runnable {

    @CommandLine.Option(names = ["--file", "-f"], required = true,
        description = ["CPK/CPB/CPI file name", "Use \"-\" to read package from standard input"])
    lateinit var fileName: String

    @CommandLine.Option(names = ["--type", "-t"],
        description = ["Package type (CPK/CPB/CPI)", "Detected from file name extension if not specified"])
    var type: PackageType? = null

    @CommandLine.Option(names = ["--version", "-v"],
        description = ["Package format version", "Detected from Manifest if not specified"])
    var format: String? = null

    @CommandLine.Option(names = ["--keystore", "-s"], required = true,
        description = ["Keystore holding trusted certificates"])
    lateinit var keyStoreFileName: String

    @CommandLine.Option(names = ["--storepass", "--password", "-p"], required = true,
        description = ["Keystore password"])
    lateinit var keyStorePass: String

    @Suppress("TooGenericExceptionCaught")
    override fun run() =
        try {
            VerifierBuilder()
                .type(type)
                .format(format)
                .name(fileName)
                .inputStream(getInputStream(fileName))
                .trustedCerts(readCertificates(keyStoreFileName, keyStorePass))
                .build()
                .verify()
            println("Successfully verified corda package")
        } catch (e: Exception) {
            println("Error verifying corda package: ${e.message}")
        }

    /**
     * Returns input stream from file [fileName] or standard input if [fileName] is "-"
     */
    private fun getInputStream(fileName: String): InputStream =
        if (fileName == "-") {
            System.`in`
        } else {
            requireFileExists(fileName)
            FileInputStream(fileName)
        }
}