package net.corda.cli.plugins.packaging

import java.nio.file.Files
import java.nio.file.Path
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.StandardOpenOption.READ
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import net.corda.cli.plugins.packaging.signing.CpxSigner
import picocli.CommandLine

@Command(
    name = "create-cpb",
    description = ["Creates a CPB from passed in CPK archives."]
)
class CreateCpb : Runnable {

    @CommandLine.Parameters(paramLabel = "CPK", description=["path of the input CPK(s)"])
    lateinit var cpks: List<String>

    @Option(names = ["--cpb-name"], required = true, description = ["CPB name"])
    lateinit var cpbName: String

    @Option(names = ["--cpb-version"], required = true, description = ["CPB version"])
    lateinit var cpbVersion: String

    @Option(names = ["--upgrade"], arity = "1", description = ["Allow upgrade without flow draining"])
    var cpbUpgrade: Boolean = false

    @Option(names = ["--file", "-f"], required = true, description = ["Output CPB file name"])
    lateinit var outputCpbFileName: String

    @Option(names = ["--keystore", "-s"], required = true, description = ["Keystore holding signing keys"])
    lateinit var keyStoreFileName: String

    @Option(names = ["--storepass", "--password", "-p"], required = true, description = ["Keystore password"])
    lateinit var keyStorePass: String

    @Option(names = ["--key", "-k"], required = true, description = ["Key alias"])
    lateinit var keyAlias: String

    @Option(names = ["--tsa", "-t"], description = ["Time Stamping Authority (TSA) URL"])
    var tsaUrl: String? = null

    companion object {
        private val CPB_NAME_ATTRIBUTE = Attributes.Name("Corda-CPB-Name")

        private val CPB_VERSION_ATTRIBUTE = Attributes.Name("Corda-CPB-Version")

        private val CPB_FORMAT_VERSION = Attributes.Name("Corda-CPB-Format")

        const val CPB_CURRENT_FORMAT_VERSION = "2.0"

        private val CPB_UPGRADE = Attributes.Name("Corda-CPB-Upgrade")

        const val CPB_FILE_EXTENSION = "cpb"

        /**
         * Name of signature within Cpb file
         */
        private const val CPB_SIGNER_NAME = "CPB-SIG"

        /**
         * Check file exists and returns a Path object pointing to the file, throws error if file does not exist
         */
        private fun requireFileExists(fileName: String): Path {
            val path = Path.of(fileName)
            require(Files.isReadable(path)) { "\"$fileName\" does not exist or is not readable" }
            return path
        }

        /**
         * Check that file does not exist and returns a Path object pointing to the filename, throws error if file exists
         */
        private fun requireFileDoesNotExist(fileName: String): Path {
            val path = Path.of(fileName)
            require(Files.notExists(path)) { "\"$fileName\" already exists" }
            return path
        }
    }

    override fun run() {
        val unsignedCpb = Files.createTempFile("buildCPB", ".$CPB_FILE_EXTENSION")
        try {
            buildUnsignedCpb(unsignedCpb, cpks)
            val cpbPath = requireFileDoesNotExist(outputCpbFileName)
            val privateKeyEntry = CpxSigner.getPrivateKeyEntry(keyStoreFileName, keyStorePass, keyAlias)
            val privateKey = privateKeyEntry.privateKey
            val certPath = CpxSigner.buildCertPath(privateKeyEntry.certificateChain.asList())
            CpxSigner.sign(unsignedCpb, cpbPath, privateKey, certPath, CPB_SIGNER_NAME, tsaUrl)
        } finally {
            Files.deleteIfExists(unsignedCpb)
        }
    }

    private fun buildUnsignedCpb(unsignedCpb: Path, cpks: List<String>) {
        cpks.forEach {
            requireFileExists(it)
        }

        val manifest = Manifest()
        val manifestMainAttributes = manifest.mainAttributes
        manifestMainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifestMainAttributes[CPB_FORMAT_VERSION] = CPB_CURRENT_FORMAT_VERSION
        manifestMainAttributes[CPB_NAME_ATTRIBUTE] = cpbName
        manifestMainAttributes[CPB_VERSION_ATTRIBUTE] = cpbVersion
        manifestMainAttributes[CPB_UPGRADE] = cpbUpgrade.toString()

        JarOutputStream(
            Files.newOutputStream(unsignedCpb, WRITE),
            manifest
        ).use { cpb ->

            cpks.map {
                Path.of(it)
            }.forEach { cpkFileName ->
                Files.newInputStream(cpkFileName, READ).use { cpk ->
                    cpb.putNextEntry(JarEntry(cpkFileName.fileName.toString()))
                    cpk.copyTo(cpb)
                    cpb.closeEntry()
                }
            }
        }
    }
}