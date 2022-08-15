package net.corda.cli.plugins.packaging

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.jar.JarInputStream
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_1
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_2
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_2_ALIAS
import net.corda.cli.plugins.packaging.TestUtils.captureStdErr
import net.corda.libs.packaging.testutils.cpb.TestCpbV1Builder
import net.corda.libs.packaging.testutils.cpk.TestCpkV1Builder
import net.corda.utilities.exists
import org.junit.jupiter.api.Assertions.assertFalse
import picocli.CommandLine.Help

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

    private lateinit var app: CreateCpi
    private val testGroupPolicy = Path.of(this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
        ?: error("TestGroupPolicy.json not found"))
    private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
        ?: error("signingkeys.pfx not found"))

    @BeforeEach
    fun setup() {
        app = CreateCpi()
        testCpb = createCpb()
    }

    private fun createCpb(): Path {
        val cpbFile = Path.of(tempDir.toString(), "test.cpb")
        val cpbStream = TestCpbV1Builder()
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
                "--sig-file=$CPI_SIGNER_NAME"
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        checkCpi(outputFile)
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
                "--sig-file=$CPI_SIGNER_NAME"
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        checkCpi(outputFile)
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
                "--sig-file=$CPI_SIGNER_NAME"
            )
        }

        // Expect output to be silent on success
        assertEquals("", errText)

        val outputFile = Path.of(tempDir.toString(), "DefaultOutputFilename.cpi")
        checkCpi(outputFile)
    }


    private fun checkCpi(outputFile: Path) {
        // Check files are present
        var groupPolicyPresent = false
        var manifestPresent = false
        var cpk1Present = false
        var cpk2Present = false
        var cpiSignaturePresent = false
        var cpbSignaturePresent = false
        JarInputStream(Files.newInputStream(outputFile), true).use {

            // Check manifest
            if (it.manifest.entries.isNotEmpty())
                manifestPresent = true

            // Check other files
            var entry = it.nextJarEntry
            while (entry != null) {
                when (entry.name) {
                    "META-INF/GroupPolicy.json" -> groupPolicyPresent = true
                    "testCpk1-1.0.0.0.cpk" -> cpk1Present = true
                    "testCpk2-2.0.0.0.cpk" -> cpk2Present = true
                    "META-INF/CPI-SIG.SF" -> cpiSignaturePresent = true
                    "META-INF/CORDAPP.SF" -> cpbSignaturePresent = true
                }

                entry = it.nextJarEntry
            }
        }

        // Check we saw all the files we expected to
        Assertions.assertAll(
            { assertTrue(groupPolicyPresent, "groupPolicyPresent") },
            { assertTrue(manifestPresent, "manifestPresent") },
            { assertTrue(cpk1Present, "cpk1Present") },
            { assertTrue(cpk2Present, "cpk2Present") },
            { assertTrue(cpiSignaturePresent, "cpiSignaturePresent") },
            { assertTrue(cpbSignaturePresent, "cpbSignaturePresent") },
        )
    }


    @Test
    @Suppress("MaxLineLength")
    fun testNoOptionError() {

        val errText = captureStdErr {
            CommandLine(app)
                .setColorScheme(Help.defaultColorScheme(Help.Ansi.OFF))
                .execute("")
        }

        assertEquals("""Missing required options: '--cpb=<cpbFileName>', '--group-policy=<groupPolicyFileName>', '--keystore=<keyStoreFileName>', '--storepass=<keyStorePass>', '--key=<keyAlias>'
Usage: create -c=<cpbFileName> [-f=<outputFileName>] -g=<groupPolicyFileName>
              -k=<keyAlias> -p=<keyStorePass> -s=<keyStoreFileName>
              [--sig-file=<_sigFile>] [-t=<tsaUrl>]
Creates a CPI from a CPB and GroupPolicy.json file.
  -c, --cpb=<cpbFileName>   CPB file to convert into CPI
  -f, --file=<outputFileName>
                            Output file
                            If omitted, the CPB filename with .cpi as a
                              filename extension is used
  -g, --group-policy=<groupPolicyFileName>
                            Group policy to include in CPI
                            Use "-" to read group policy from standard input
  -k, --key=<keyAlias>      Key alias
  -p, --password, --storepass=<keyStorePass>
                            Keystore password
  -s, --keystore=<keyStoreFileName>
                            Keystore holding signing keys
      --sig-file=<_sigFile> Base file name for signature related files
  -t, --tsa=<tsaUrl>        Time Stamping Authority (TSA) URL
""", errText)
    }

    @Test
    fun `cpi create tool aborts if its not a cpb before packing it into a cpi`() {
        // Attempt to pack a Cpk into a Cpi - should fail since it's not a Cpb
        val cpkBuilder = TestCpkV1Builder()
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
                "--sig-file=${CPI_SIGNER_NAME}"
            )
        }

        assertFalse(cpiOutputFile.exists())
        assertTrue(outText.contains("java.lang.IllegalArgumentException: Cpb is invalid"))
        assertTrue(
            outText.contains(
                "net.corda.libs.packaging.core.exception.CordappManifestException: " +
                        "Manifest is missing required attribute \"Corda-CPB-Name\""
            )
        )
    }
}