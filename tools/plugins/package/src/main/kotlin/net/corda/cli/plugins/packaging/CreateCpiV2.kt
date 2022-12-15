package net.corda.cli.plugins.packaging

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.io.FileInputStream
import java.lang.IllegalArgumentException
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
import net.corda.cli.plugins.packaging.signing.SigningHelpers
import net.corda.cli.plugins.packaging.signing.SigningOptions
import net.corda.cli.plugins.packaging.signing.CertificateLoader.readCertificates
import net.corda.libs.packaging.verify.PackageType
import net.corda.libs.packaging.verify.VerifierBuilder
import net.corda.libs.packaging.verify.internal.VerifierFactory
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

private const val READ_FROM_STDIN = "-"

/**
 * Creates a CPI v2 from a CPB and GroupPolicy.json file.
 */
@Command(
    name = "create-cpi",
    description = ["Creates a CPI v2 from a CPB and GroupPolicy.json file."]
)
class CreateCpiV2 : Runnable {

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
    override fun run() {
        // Check input files exist
        requireFileExists(signingOptions.keyStoreFileName)

        val groupPolicyString = if (groupPolicyFileName == READ_FROM_STDIN)
            System.`in`.readAllBytes().toString(Charsets.UTF_8)
        else {
            File(requireFileExists(groupPolicyFileName).toString()).readText(Charsets.UTF_8)
        }

        GroupPolicyValidator.validateGroupPolicy(groupPolicyString)

        // Check input Cpb file is indeed a Cpb
        var cpbPath: Path? = null
        if (cpbFileName != null) {
            cpbPath = requireFileExists(cpbFileName as String)
            verifyIsValidCpbV2(cpbPath)
        }

        // Create output filename if none specified
        var outputName = outputFileName
        if (outputName == null) {
            val cpiFilename = "${File(cpbFileName).nameWithoutExtension}$CPI_EXTENSION"
            if (cpbPath != null) {
                val cpbDirectory = cpbPath.toAbsolutePath().parent.toString()
                outputName = Path.of(cpbDirectory, cpiFilename).toString()
            } else {
                throw IllegalArgumentException("Must specify an Output File if no CPB is provided.")
            }
        }
        // Check output Cpi file does not exist
        val outputFilePath = requireFileDoesNotExist(outputName)

        buildAndSignCpi(cpbPath, outputFilePath, groupPolicyString)
    }

    /**
     * @throws IllegalArgumentException if it fails to verify Cpb V2
     */
    private fun verifyIsValidCpbV2(cpbPath: Path) {
        try {
            VerifierBuilder()
                .type(PackageType.CPB)
                .format(VerifierFactory.FORMAT_2)
                .name(cpbPath.toString())
                .inputStream(FileInputStream(cpbPath.toString()))
                .trustedCerts(readCertificates(signingOptions.keyStoreFileName, signingOptions.keyStorePass))
                .build()
                .verify()
        } catch (e: Exception) {
            throw IllegalArgumentException("Cpb is invalid", e)
        }
    }

    /**
     * Build and sign CPI file
     *
     * Creates a temporary file, copies CPB into temporary file, adds group policy then signs
     */
    private fun buildAndSignCpi(cpbPath: Path?, outputFilePath: Path, groupPolicy: String) {
        val unsignedCpi = Files.createTempFile("buildCPI", null)
        try {
            // Build unsigned CPI jar
            buildUnsignedCpi(cpbPath, unsignedCpi, groupPolicy)

            // Sign CPI jar
            SigningHelpers.sign(
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
    private fun buildUnsignedCpi(cpbPath: Path?, unsignedCpi: Path, groupPolicy: String) {
        val manifest = Manifest()
        val manifestMainAttributes = manifest.mainAttributes
        manifestMainAttributes[Attributes.Name.MANIFEST_VERSION] = MANIFEST_VERSION
        manifestMainAttributes[CPI_FORMAT_ATTRIBUTE_NAME] = CPI_FORMAT_ATTRIBUTE
        manifestMainAttributes[CPI_NAME_ATTRIBUTE_NAME] = cpiName
        manifestMainAttributes[CPI_VERSION_ATTRIBUTE_NAME] = cpiVersion
        manifestMainAttributes[CPI_UPGRADE_ATTRIBUTE_NAME] = cpiUpgrade.toString()

        JarOutputStream(Files.newOutputStream(unsignedCpi, WRITE), manifest).use { cpiJar ->
            if (cpbPath != null){
                // Copy the CPB contents
                cpiJar.putNextEntry(JarEntry(cpbPath.fileName.toString()))
                Files.newInputStream(cpbPath, READ).use {
                    it.copyTo(cpiJar)
                }
            }

            // Add group policy
            addGroupPolicy(cpiJar, groupPolicy)
        }
    }

    /**
     * Adds group policy file to jar file
     *
     * Reads group policy from stdin or file depending on user choice
     */
    private fun addGroupPolicy(cpiJar: JarOutputStream, groupPolicy: String) {
        cpiJar.putNextEntry(JarEntry(META_INF_GROUP_POLICY_JSON))
        cpiJar.write(groupPolicy.toByteArray())
        cpiJar.closeEntry()
    }
}
