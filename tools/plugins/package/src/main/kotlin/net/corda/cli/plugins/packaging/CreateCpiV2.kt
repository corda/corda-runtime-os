package net.corda.cli.plugins.packaging

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import net.corda.cli.plugins.packaging.FileHelpers.requireFileDoesNotExist
import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.cli.plugins.packaging.signing.CpxSigner
import net.corda.cli.plugins.packaging.signing.SigningOptions
import picocli.CommandLine

/**
 * Filename of group policy within jar file
 */
private const val META_INF_GROUP_POLICY_JSON = "META-INF/GroupPolicy.json"

private const val CPI_EXTENSION = ".cpi"

internal const val MANIFEST_VERSION = "1.0"

internal val CPI_FORMAT_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Format")

internal const val CPI_FORMAT_ATTRIBUTE = "2.0"

internal val CPI_NAME_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Name")

internal val CPI_VERSION_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Version")

internal val CPI_UPGRADE_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Upgrade")

/**
 * Creates a CPI v2 from a CPB and GroupPolicy.json file.
 */
@Command(
    name = "create-cpi",
    description = ["Creates a CPI v2 from a CPB and GroupPolicy.json file."]
)
class CreateCpiV2 : Runnable {

    @Option(names = ["--cpb", "-c"], required = true, description = ["CPB file to convert into CPI"])
    lateinit var cpbFileName: String

    @Option(
        names = ["--group-policy", "-g"],
        required = true,
        description = ["Group policy to include in CPI", "Use \"-\" to read group policy from standard input"]
    )
    lateinit var groupPolicyFileName: String

    @Option(names = ["--cpi-name"], required = true, description = ["CPI name (manifest attribute)"])
    lateinit var cpiName: String

    @Option(names = ["--cpi-version"], required = true, description = ["CPI version (manifest attribute)"])
    lateinit var cpiVersion: String

    @Option(names = ["--upgrade"], arity = "1", description = ["Allow upgrade without flow draining"])
    var cpiUpgrade: Boolean = false

    @Option(
        names = ["--file", "-f"],
        description = ["Output file", "If omitted, the CPB filename with .cpi as a filename extension is used"]
    )
    var outputFileName: String? = null

    @CommandLine.Mixin
    var signingOptions = SigningOptions()

    /**
     * Represents option to read group policy from file or stdin
     */
    private sealed class GroupPolicySource {
        /**
         * Read group policy from stdin
         */
        object StdIn : GroupPolicySource()

        /**
         * Read group policy from file
         */
        class File(val path: Path) : GroupPolicySource()
    }

    /**
     * Check user supplied options, then start the process of building and signing the CPI
     */
    override fun run() {

        // Check input files exist, output file does not exist
        val cpbPath = requireFileExists(cpbFileName)
        requireFileExists(signingOptions.keyStoreFileName)

        // Create output filename if none specified
        var outputName = outputFileName
        if (outputName == null) {
            val cpbDirectory = cpbPath.toAbsolutePath().parent.toString()
            val cpiFilename = "${File(cpbFileName).nameWithoutExtension}$CPI_EXTENSION"
            outputName = Path.of(cpbDirectory, cpiFilename).toString()
        }
        val outputFilePath = requireFileDoesNotExist(outputName)

        // Allow piping group policy file into stdin
        val groupPolicy = if (groupPolicyFileName == "-")
            GroupPolicySource.StdIn
        else
            GroupPolicySource.File(requireFileExists(groupPolicyFileName))

        buildAndSignCpi(cpbPath, outputFilePath, groupPolicy)
    }

    /**
     * Build and sign CPI file
     *
     * Creates a temporary file, copies CPB into temporary file, adds group policy then signs
     */
    private fun buildAndSignCpi(cpbPath: Path, outputFilePath: Path, groupPolicy: GroupPolicySource) {
        val unsignedCpi = Files.createTempFile("buildCPI", null)
        try {
            // Build unsigned CPI jar
            buildUnsignedCpi(cpbPath, unsignedCpi, groupPolicy)

            // Sign CPI jar
            CpxSigner.sign(
                unsignedCpi,
                outputFilePath,
                signingOptions.keyStoreFileName,
                signingOptions.keyStorePass,
                signingOptions.keyAlias,
                signingOptions.sigFile,
                signingOptions.tsaUrl
            )
        } finally {
            // Delete temp file
            Files.deleteIfExists(unsignedCpi)
        }
    }

    /**
     * Build unsigned CPI file
     *
     * Copies CPB into new jar file and then adds group policy
     */
    private fun buildUnsignedCpi(cpbPath: Path, unsignedCpi: Path, groupPolicy: GroupPolicySource) {
        val manifest = Manifest()
        val manifestMainAttributes = manifest.mainAttributes
        manifestMainAttributes[Attributes.Name.MANIFEST_VERSION] = MANIFEST_VERSION
        manifestMainAttributes[CPI_FORMAT_ATTRIBUTE_NAME] = CPI_FORMAT_ATTRIBUTE
        manifestMainAttributes[CPI_NAME_ATTRIBUTE_NAME] = cpiName
        manifestMainAttributes[CPI_VERSION_ATTRIBUTE_NAME] = cpiVersion
        manifestMainAttributes[CPI_UPGRADE_ATTRIBUTE_NAME] = cpiUpgrade.toString()

        JarOutputStream(Files.newOutputStream(unsignedCpi, WRITE), manifest).use { cpiJar ->

            // Copy the CPB contents
            cpiJar.putNextEntry(JarEntry(cpbPath.fileName.toString()))
            Files.newInputStream(cpbPath, READ).copyTo(cpiJar)

            // Add group policy
            addGroupPolicy(cpiJar, groupPolicy)
        }
    }

    /**
     * Adds group policy file to jar file
     *
     * Reads group policy from stdin or file depending on user choice
     */
    private fun addGroupPolicy(cpiJar: JarOutputStream, groupPolicy: GroupPolicySource) {
        cpiJar.putNextEntry(JarEntry(META_INF_GROUP_POLICY_JSON))
        when (groupPolicy) {
            is GroupPolicySource.File -> Files.copy(groupPolicy.path, cpiJar)
            is GroupPolicySource.StdIn -> System.`in`.copyTo(cpiJar)
        }
        cpiJar.closeEntry()
    }
}
