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
import net.corda.cli.plugins.packaging.FileHelpers.requireFileDoesNotExist
import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.cli.plugins.packaging.signing.SigningHelpers
import net.corda.cli.plugins.packaging.signing.SigningOptions
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

    @Option(names = ["--cpb-version"], description=["CPB version"], converter = [VersionConverter::class])
    var cpbVersion: Int =1

    @Option(names = ["--upgrade"], arity = "1", description = ["Allow upgrade without flow draining"])
    var cpbUpgrade: Boolean = false

    @Option(names = ["--file", "-f"], required = true, description = ["Output CPB file name"])
    lateinit var outputCpbFileName: String

    @CommandLine.Mixin
    var signingOptions = SigningOptions()

    internal companion object {
        private const val MANIFEST_VERSION = "1.0"

        private const val CPB_CURRENT_FORMAT_VERSION = "2.0"

        private val CPB_NAME_ATTRIBUTE = Attributes.Name("Corda-CPB-Name")

        private val CPB_VERSION_ATTRIBUTE = Attributes.Name("Corda-CPB-Version")

        private val CPB_FORMAT_VERSION = Attributes.Name("Corda-CPB-Format")

        private val CPB_UPGRADE = Attributes.Name("Corda-CPB-Upgrade")
    }

    override fun run() {
        val unsignedCpb = Files.createTempFile("buildCPB", null)
        try {
            buildUnsignedCpb(unsignedCpb, cpks)
            val cpbPath = requireFileDoesNotExist(outputCpbFileName)

            SigningHelpers.sign(
                unsignedCpb,
                cpbPath,
                signingOptions.keyStoreFileName,
                signingOptions.keyStorePass,
                signingOptions.keyAlias,
                signingOptions.sigFile,
                signingOptions.tsaUrl
            )
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
        manifestMainAttributes[Attributes.Name.MANIFEST_VERSION] = MANIFEST_VERSION
        manifestMainAttributes[CPB_FORMAT_VERSION] = CPB_CURRENT_FORMAT_VERSION
        manifestMainAttributes[CPB_NAME_ATTRIBUTE] = cpbName
        manifestMainAttributes[CPB_VERSION_ATTRIBUTE] = cpbVersion.toString()
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