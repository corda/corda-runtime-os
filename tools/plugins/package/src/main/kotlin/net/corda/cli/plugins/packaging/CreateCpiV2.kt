package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.FileHelpers.requireFileDoesNotExist
import net.corda.cli.plugins.packaging.FileHelpers.requireFileExists
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.sdk.packaging.CreateCpiV2
import net.corda.sdk.packaging.GroupPolicyValidator
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.ExitCode
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.jar.Attributes

private const val CPI_EXTENSION = ".cpi"

internal val CPI_FORMAT_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Format")

internal const val CPI_FORMAT_ATTRIBUTE = "2.0"

internal val CPI_NAME_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Name")

internal val CPI_VERSION_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Version")

internal val CPI_UPGRADE_ATTRIBUTE_NAME = Attributes.Name("Corda-CPI-Upgrade")

private const val READ_FROM_STDIN = "-"

/**
 * Creates a CPI v2 from a CPB and GroupPolicy.json file.
 */
@Command(
    name = "create-cpi",
    description = ["Creates a CPI v2 from a CPB and GroupPolicy.json file."],
    mixinStandardHelpOptions = true
)
class CreateCpiV2 : Callable<Int> {

    @Option(names = ["--cpb", "-c"], required = false, description = ["CPB file to convert into CPI"])
    var cpbFileName: String? = null

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
     * Check user supplied options, then start the process of building and signing the CPI
     */
    override fun call(): Int {
        // Check input files exist
        requireFileExists(signingOptions.keyStoreFileName)

        val groupPolicyString = if (groupPolicyFileName == READ_FROM_STDIN)
            System.`in`.readAllBytes().toString(Charsets.UTF_8)
        else
            File(requireFileExists(groupPolicyFileName).toString()).readText(Charsets.UTF_8)

        GroupPolicyValidator.validateGroupPolicy(groupPolicyString)

        val cpbPath = cpbFileName?.let { requireFileExists(it) }

        // Check input Cpb file is indeed a Cpb
        cpbPath?.let {
            try {
                CreateCpiV2.verifyIsValidCpbV2(it, signingOptions.asSigningParameters)
            } catch (e: Exception) {
                System.err.println("Error verifying CPB: ${e.message}")
                return ExitCode.SOFTWARE
            }
        }

        val outputName = determineOutputFileName(cpbPath)

        // Check output Cpi file does not exist
        val outputFilePath = requireFileDoesNotExist(outputName)

        CreateCpiV2.buildAndSignCpi(
            cpbPath,
            outputFilePath,
            groupPolicyString,
            cpiName,
            cpiVersion,
            cpiUpgrade,
            signingOptions.asSigningParameters
        )
        return ExitCode.OK
    }

    private fun determineOutputFileName(cpbPath: Path?) : String {
        // Try create output filename if none specified
        var outputName = outputFileName
        if (outputName == null) {
            if (cpbPath != null) {
                val cpiFilename = "${File(cpbFileName!!).nameWithoutExtension}$CPI_EXTENSION"
                val cpbDirectory = cpbPath.toAbsolutePath().parent.toString()
                outputName = Path.of(cpbDirectory, cpiFilename).toString()
            } else {
                throw IllegalArgumentException("Must specify an Output File if no CPB is provided.")
            }
        }
        return outputName
    }
}