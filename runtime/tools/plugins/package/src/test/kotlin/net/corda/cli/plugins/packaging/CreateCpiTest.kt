package net.corda.cli.plugins.packaging

import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_1
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_2
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_2_ALIAS
import net.corda.cli.plugins.packaging.TestUtils.captureStdErr
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.libs.packaging.testutils.cpk.TestCpkV2Builder
import net.corda.utilities.exists
import net.corda.utilities.inputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import picocli.CommandLine.Help
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.jar.JarEntry
import java.util.jar.JarInputStream

class CreateCpiTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var testCpb: Path

    companion object {
        const val CPI_FILE_NAME = "output.cpi"

        private const val CPK_SIGNER_NAME = "CPK-SIG"
        private const val CPB_SIGNER_NAME = "CORDAPP"
        private const val CPI_SIGNER_NAME = "CPI-SIG"
        private val CPK_SIGNER = net.corda.libs.packaging.testutils.TestUtils.Signer(CPK_SIGNER_NAME, SIGNING_KEY_2)
        private val CPB_SIGNER = net.corda.libs.packaging.testutils.TestUtils.Signer(CPB_SIGNER_NAME, SIGNING_KEY_1)
    }

    private lateinit var app: CreateCpiV2
    private val testGroupPolicy = Path.of(this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
        ?: error("TestGroupPolicy.json not found"))
    private val invalidTestGroupPolicy = Path.of(this::class.java.getResource("/InvalidTestGroupPolicy.json")?.toURI()
        ?: error("InvalidTestGroupPolicy.json not found"))
    private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
        ?: error("signingkeys.pfx not found"))

    @BeforeEach
    fun setup() {
        app = CreateCpiV2()
        testCpb = createCpb()
    }

    private fun createCpb(): Path {
        val cpbFile = Path.of(tempDir.toString(), "test.cpb")
        val cpbStream = TestCpbV2Builder()
            .signers(CPB_SIGNER)
            .build()
            .inputStream()

        Files.newOutputStream(cpbFile, StandardOpenOption.CREATE_NEW).use { outStream ->
            cpbStream.use {
                it.copyTo(outStream)
            }
        }

        return cpbFile
    }

    private fun tmpOutputFile() = Path.of(tempDir.toString(), "output.cpi")

    private val testCpbExpectedFilenames = setOf(
        "META-INF/CPI-SIG.SF",
        "META-INF/CPI-SIG.EC",
        "META-INF/GroupPolicy.json",
        "test.cpb",
        "test.cpb/META-INF/CORDAPP.SF",
        "test.cpb/META-INF/CORDAPP.EC",
        "test.cpb/testCpk1-1.0.0.0.jar",
        "test.cpb/testCpk2-2.0.0.0.jar"
    )

    @Test
    fun buildCpiLongParameterNames() {
        val outputFile = tmpOutputFile()

        val errText = captureStdErr {
            CommandLine(app).execute(
                "--cpb=${testCpb}",
                "--group-policy=${testGroupPolicy}",
                "--file=$outputFile",
                "--keystore=${testKeyStore}",
                "--storepass=keystore password",
                "--key=$SIGNING_KEY_2_ALIAS",
                "--sig-file=$CPI_SIGNER_NAME",
                "--cpi-name=cpi name",
                "--cpi-version=1.2.3"
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        checkCpi(outputFile, testCpbExpectedFilenames)
    }

    @Test
    fun buildCpiShortParameterNames() {
        val outputFile = tmpOutputFile()

        val errText = captureStdErr {
            CommandLine(app).execute(
                "-c=${testCpb}",
                "-g=${testGroupPolicy}",
                "-f=$outputFile",
                "-s=${testKeyStore}",
                "-p=keystore password",
                "-k=$SIGNING_KEY_2_ALIAS",
                "--sig-file=$CPI_SIGNER_NAME",
                "--cpi-name=cpi name",
                "--cpi-version=1.2.3"
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        checkCpi(outputFile, testCpbExpectedFilenames)
    }

    @Test
    fun buildCpiDefaultOutputFilename() {
        val defaultOutputTestCpb: Path = Path.of(tempDir.toString(), "DefaultOutputFilename.cpb")
        Files.copy(testCpb, defaultOutputTestCpb, StandardCopyOption.COPY_ATTRIBUTES)

        val errText = captureStdErr {
            CommandLine(app).execute(
                "-c=${defaultOutputTestCpb}",
                "-g=${testGroupPolicy}",
                "-s=${testKeyStore}",
                "-p=keystore password",
                "-k=$SIGNING_KEY_2_ALIAS",
                "--sig-file=$CPI_SIGNER_NAME",
                "--cpi-name=cpi name",
                "--cpi-version=1.2.3"
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        val outputFile = Path.of(tempDir.toString(), "DefaultOutputFilename.cpi")
        val expectedFilenames = setOf(
            "META-INF/CPI-SIG.SF",
            "META-INF/CPI-SIG.EC",
            "META-INF/GroupPolicy.json",
            "DefaultOutputFilename.cpb",
            "DefaultOutputFilename.cpb/META-INF/CORDAPP.SF",
            "DefaultOutputFilename.cpb/META-INF/CORDAPP.EC",
            "DefaultOutputFilename.cpb/testCpk1-1.0.0.0.jar",
            "DefaultOutputFilename.cpb/testCpk2-2.0.0.0.jar"
        )
        checkCpi(outputFile, expectedFilenames)
    }

    @Test
    fun `cpi create tool handles group policy passed through standard input`() {
        val outputFile = tmpOutputFile()
        val groupPolicyString = File(testGroupPolicy.toString()).readText(Charsets.UTF_8)

        val systemIn = System.`in`
        val testIn = ByteArrayInputStream(groupPolicyString.toByteArray())
        System.setIn(testIn)

        val errText = captureStdErr {
            CommandLine(app).execute(
                "--cpb=${testCpb}",
                "--group-policy=-",
                "--file=$outputFile",
                "--keystore=${testKeyStore}",
                "--storepass=keystore password",
                "--key=$SIGNING_KEY_2_ALIAS",
                "--sig-file=$CPI_SIGNER_NAME",
                "--cpi-name=cpi name",
                "--cpi-version=1.2.3"
            )
        }

        System.setIn(systemIn)
        // Expect output to be silent on success
        assertEquals("", errText)
        checkCpi(outputFile, testCpbExpectedFilenames)
    }

    private fun checkCpi(outputFile: Path, expectedFilenames: Set<String>) {
        // Check files are present
        var manifestPresent = false
        val fileNames = JarInputStream(outputFile.inputStream(), true).use { cpi ->

            // Check manifest
            if (cpi.manifest.entries.isNotEmpty())
                manifestPresent = true

            // Check other files
            generateSequence { cpi.nextJarEntry }
                .flatMap { cpiEntry ->
                    if (cpiEntry.name.endsWith(".cpb")) {
                        // This entry is a CPB. Read the filenames inside.
                        //
                        // We do not close the CPB JarInputStream because it would close the CPI JarInputStream and we
                        // need that open to continue reading the CPI.
                        readCpbFilenames(cpi, cpiEntry)
                    } else {
                        sequenceOf(cpiEntry.name)
                    }
                }
                .toSet()
        }

        // Check we saw all the files we expected to
        Assertions.assertAll(
            { assertTrue(manifestPresent, "manifestPresent") },
            { assertEquals(expectedFilenames, fileNames) }
        )
    }

    private fun readCpbFilenames(cpi: JarInputStream, cpiEntry: JarEntry): Sequence<String> =
        JarInputStream(cpi, true).let { cpb ->
            val cpbFilename = cpiEntry.name
            val filesInsideCpb = generateSequence { cpb.nextJarEntry }.map { "$cpbFilename/${it.name}" }
            sequenceOf(cpbFilename).plus(filesInsideCpb)
        }

    @Test
    @Suppress("MaxLineLength")
    fun testNoOptionError() {

        val errText = captureStdErr {
            CommandLine(app)
                .setColorScheme(Help.defaultColorScheme(Help.Ansi.OFF))
                .execute("")
        }

        assertEquals("""Missing required options: '--group-policy=<groupPolicyFileName>', '--cpi-name=<cpiName>', '--cpi-version=<cpiVersion>', '--keystore=<keyStoreFileName>', '--storepass=<keyStorePass>', '--key=<keyAlias>'
Usage: create-cpi [-c=<cpbFileName>] --cpi-name=<cpiName>
                  --cpi-version=<cpiVersion> [-f=<outputFileName>]
                  -g=<groupPolicyFileName> -k=<keyAlias> -p=<keyStorePass>
                  -s=<keyStoreFileName> [--sig-file=<_sigFile>] [-t=<tsaUrl>]
                  [--upgrade=<cpiUpgrade>]
Creates a CPI v2 from a CPB and GroupPolicy.json file.
  -c, --cpb=<cpbFileName>    CPB file to convert into CPI
      --cpi-name=<cpiName>   CPI name (manifest attribute)
      --cpi-version=<cpiVersion>
                             CPI version (manifest attribute)
  -f, --file=<outputFileName>
                             Output file
                             If omitted, the CPB filename with .cpi as a
                               filename extension is used
  -g, --group-policy=<groupPolicyFileName>
                             Group policy to include in CPI
                             Use "-" to read group policy from standard input
  -k, --key=<keyAlias>       Key alias
  -p, --password, --storepass=<keyStorePass>
                             Keystore password
  -s, --keystore=<keyStoreFileName>
                             Keystore holding signing keys
      --sig-file=<_sigFile>  Base file name for signature related files
  -t, --tsa=<tsaUrl>         Time Stamping Authority (TSA) URL
      --upgrade=<cpiUpgrade> Allow upgrade without flow draining
""", errText)
    }

    @Test
    fun `cpi create tool aborts if its not a cpb before packing it into a cpi`() {
        // Attempt to pack a Cpk into a Cpi - should fail since it's not a Cpb
        val cpkBuilder = TestCpkV2Builder()
        val cpkStream = cpkBuilder
            .signers(CPK_SIGNER).build().inputStream()
        val cpkPath = Path.of(tempDir.toString(), cpkBuilder.name)
        Files.newOutputStream(cpkPath, StandardOpenOption.CREATE_NEW).use { outStream ->
            cpkStream.use {
                it.copyTo(outStream)
            }
        }

        val cpiOutputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)

        val outText = captureStdErr {
            CommandLine(app).execute (
                "--cpb=${cpkPath}",
                "--group-policy=${testGroupPolicy}",
                "--file=$cpiOutputFile",
                "--keystore=${testKeyStore}",
                "--storepass=keystore password",
                "--key=$SIGNING_KEY_2_ALIAS",
                "--sig-file=${CPI_SIGNER_NAME}",
                "--cpi-name=cpi name",
                "--cpi-version=1.2.3"
            )
        }

        assertFalse(cpiOutputFile.exists())
        assertThat(outText).contains("java.lang.IllegalArgumentException: Cpb is invalid")
        assertThat(outText).contains("net.corda.libs.packaging.core.exception.CordappManifestException: " +
                "Manifest has invalid attribute \"Corda-CPB-Format\" value \"null\"")
    }

    @Test
    fun `cpi create tool aborts if its group policy is invalid`() {
        val outputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)

        val outText = captureStdErr {
            CommandLine(app).execute(
                "--cpb=${testCpb}",
                "--group-policy=${invalidTestGroupPolicy}",
                "--file=$outputFile",
                "--keystore=${testKeyStore}",
                "--storepass=keystore password",
                "--key=$SIGNING_KEY_2_ALIAS",
                "--sig-file=$CPI_SIGNER_NAME",
                "--cpi-name=cpi name",
                "--cpi-version=1.2.3"
            )
        }

        assertFalse(outputFile.exists())
        assertTrue(outText.contains("MembershipSchemaValidationException: Exception when validating membership schema"))
        assertTrue(
            outText.contains(
                "Failed to validate against schema \"corda.group.policy\" due to the following error(s): " +
                        "[\$.groupId: does not match the regex pattern")
        )
    }
}