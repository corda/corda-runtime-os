package net.corda.cli.plugins.packaging

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import net.corda.cli.plugins.packaging.signing.CpxSigner

/**
 * Filename of group policy within jar file
 */
private const val META_INF_GROUP_POLICY_JSON = "META-INF/GroupPolicy.json"

/**
 * Name of signature within jar file
 */
private const val SIGNER_NAME = "CPI-SIG"

private const val CPI_EXTENSION = ".cpi"

/**
 * Creates a CPI from a CPB and GroupPolicy.json file.
 */
@Command(
    name = "create",
    description = ["Creates a CPI from a CPB and GroupPolicy.json file."]
)
class CreateCpi : Runnable {

    @Option(names = ["--cpb", "-c"], required = true, description = ["CPB file to convert into CPI"])
    lateinit var cpbFileName: String

    @Option(
        names = ["--group-policy", "-g"],
        required = true,
        description = ["Group policy to include in CPI", "Use \"-\" to read group policy from standard input"]
    )
    lateinit var groupPolicyFileName: String

    @Option(
        names = ["--file", "-f"],
        description = ["Output file", "If omitted, the CPB filename with .cpi as a filename extension is used"]
    )
    var outputFileName: String? = null

    @Option(names = ["--keystore", "-s"], required = true, description = ["Keystore holding signing keys"])
    lateinit var keyStoreFileName: String

    @Option(names = ["--storepass", "--password", "-p"], required = true, description = ["Keystore password"])
    lateinit var keyStorePass: String

    @Option(names = ["--key", "-k"], required = true, description = ["Key alias"])
    lateinit var keyAlias: String

    @Option(names = ["--tsa", "-t"], description = ["Time Stamping Authority (TSA) URL"])
    var tsaUrl: String? = null

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
        val cpbPath = checkFileExists(cpbFileName)
        checkFileExists(keyStoreFileName)

        // Create output filename if none specified
        var outputName = outputFileName
        if (outputName == null) {
            val cpbDirectory = cpbPath.toAbsolutePath().parent.toString()
            val cpiFilename = "${File(cpbFileName).nameWithoutExtension}$CPI_EXTENSION"
            outputName = Path.of(cpbDirectory, cpiFilename).toString()
        }
        val outputFilePath = checkFileDoesNotExist(outputName)

        // Allow piping group policy file into stdin
        val groupPolicy = if (groupPolicyFileName == "-")
            GroupPolicySource.StdIn
        else
            GroupPolicySource.File(checkFileExists(groupPolicyFileName))

        buildAndSignCpi(cpbPath, outputFilePath, groupPolicy)
    }

    /**
     * Check file exists and returns a Path object pointing to the file, throws error if file does not exist
     */
    private fun checkFileExists(fileName: String): Path {
        val path = Path.of(fileName)
        require(Files.isReadable(path)) { "\"$fileName\" does not exist or is not readable" }
        return path
    }

    /**
     * Check that file does not exist and returns a Path object pointing to the filename, throws error if file exists
     */
    private fun checkFileDoesNotExist(fileName: String): Path {
        val path = Path.of(fileName)
        require(Files.notExists(path)) { "\"$fileName\" already exists" }
        return path
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
            val privateKeyEntry = CpxSigner.getPrivateKeyEntry(keyStoreFileName, keyStorePass, keyAlias)
            val privateKey = privateKeyEntry.privateKey
            val certPath = CpxSigner.buildCertPath(privateKeyEntry.certificateChain.asList())
            CpxSigner.sign(unsignedCpi, outputFilePath, privateKey, certPath, SIGNER_NAME, tsaUrl)
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
        JarInputStream(Files.newInputStream(cpbPath, READ)).use { cpbJar ->
            JarOutputStream(Files.newOutputStream(unsignedCpi, WRITE), cpbJar.manifest).use { cpiJar ->

                // Copy the CPB contents
                copyJarContents(cpbJar, cpiJar)

                // Add group policy
                addGroupPolicy(cpiJar, groupPolicy)
            }
        }
    }

    /**
     * Copy contents of one jar to another
     */
    private fun copyJarContents(inputJar: JarInputStream, outputJar: JarOutputStream) {
        var entry = inputJar.nextJarEntry
        while (entry != null) {
            // Copy entry
            outputJar.putNextEntry(entry)
            inputJar.copyTo(outputJar)
            outputJar.closeEntry()

            // Move to next input entry
            entry = inputJar.nextJarEntry
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
