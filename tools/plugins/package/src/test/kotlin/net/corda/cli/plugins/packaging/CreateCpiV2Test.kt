package net.corda.cli.plugins.packaging

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Attributes
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_1
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_1_ALIAS
import net.corda.cli.plugins.packaging.TestSigningKeys.SIGNING_KEY_2
import net.corda.cli.plugins.packaging.TestUtils.assertContainsAllManifestAttributes
import net.corda.cli.plugins.packaging.TestUtils.jarEntriesExistInCpx
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.libs.packaging.testutils.cpk.TestCpkV2Builder
import net.corda.utilities.exists
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine

class CreateCpiV2Test {

    @TempDir
    lateinit var tempDir: Path

    companion object {

        // Share cpb across all tests since we only read it and not modify it to save disk writes
        @TempDir
        lateinit var commonTempDir: Path

        lateinit var cpbPath: Path

        const val CPI_FILE_NAME = "output.cpi"

        const val CPK_SIGNER_NAME = "CPK-SIG"
        const val CPB_SIGNER_NAME = "CPB-SIG"
        const val CPI_SIGNER_NAME = "CPI-SIG"
        private val CPK_SIGNER = net.corda.libs.packaging.testutils.TestUtils.Signer(
            CPK_SIGNER_NAME,
            SIGNING_KEY_2
        )
        private val CPB_SIGNER = net.corda.libs.packaging.testutils.TestUtils.Signer(
            CPB_SIGNER_NAME,
            SIGNING_KEY_1
        )

        private val testGroupPolicy = Path.of(this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
            ?: error("TestGroupPolicy.json not found"))
        private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
            ?: error("signingkeys.pfx not found"))

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Create a single cpb to be used for all tests
            val cpbFile = Path.of(commonTempDir.toString(), "test.cpb")
            val cpbStream = TestCpbV2Builder()
                .signers(CPB_SIGNER)
                .build()
                .inputStream()

            Files.newOutputStream(cpbFile, StandardOpenOption.CREATE_NEW).use { outStream ->
                cpbStream.use {
                    it.copyTo(outStream)
                }
            }
            cpbPath = cpbFile
        }
    }

    @Test
    fun `cpi v2 contains cpb, manifest, signature files and GroupPolicy file`() {
        val outputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)
        CommandLine(CreateCpiV2()).execute (
            "--cpb=${cpbPath}",
            "--group-policy=${testGroupPolicy}",
            "--cpi-name=testCpi",
            "--cpi-version=5.0.0.0-SNAPSHOT",
            "--file=$outputFile",
            "--keystore=${testKeyStore}",
            "--storepass=keystore password",
            "--key=${SIGNING_KEY_1_ALIAS}",
            "--sig-file=$CPI_SIGNER_NAME"
        )

        assertTrue(
            jarEntriesExistInCpx(
                outputFile,
                listOf(
                    "META-INF/MANIFEST.MF",
                    "META-INF/$CPI_SIGNER_NAME.SF",
                    "META-INF/$CPI_SIGNER_NAME.EC",
                    "META-INF/GroupPolicy.json",
                    cpbPath.fileName.toString()
                )
            )
        )
    }

    @Test
    fun `cpi v2 contains manifest attributes`() {
        val cpiOutputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)
        CommandLine(CreateCpiV2()).execute (
            "--cpb=${cpbPath}",
            "--group-policy=${testGroupPolicy}",
            "--cpi-name=testCpi",
            "--cpi-version=5.0.0.0-SNAPSHOT",
            "--file=$cpiOutputFile",
            "--keystore=${testKeyStore}",
            "--storepass=keystore password",
            "--key=${SIGNING_KEY_1_ALIAS}",
            "--sig-file=$CPI_SIGNER_NAME"
        )

        assertContainsAllManifestAttributes(
            cpiOutputFile,
            mapOf(
                Attributes.Name.MANIFEST_VERSION to "1.0",
                CPI_FORMAT_ATTRIBUTE_NAME to CPI_FORMAT_ATTRIBUTE,
                CPI_NAME_ATTRIBUTE_NAME to "testCpi",
                CPI_VERSION_ATTRIBUTE_NAME to "5.0.0.0-SNAPSHOT",
                CPI_UPGRADE_ATTRIBUTE_NAME to "false"
            )
        )
    }

    @Test
    fun `cpi create tool aborts if its not a cpb before packing it into a cpi`() {
        // Attempt to pack a Cpk into a Cpi - should fail since it's not a Cpb
        val cpkBuilder = TestCpkV2Builder()
        val cpkStream = cpkBuilder
            .signers(CPK_SIGNER).build().inputStream()
        val cpkPath = Path.of(commonTempDir.toString(), cpkBuilder.name)
        Files.newOutputStream(cpkPath, StandardOpenOption.CREATE_NEW).use { outStream ->
            cpkStream.use {
                it.copyTo(outStream)
            }
        }

        val cpiOutputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)

        val outText = TestUtils.captureStdErr {
            CommandLine(CreateCpiV2()).execute (
                "--cpb=${cpkPath}",
                "--group-policy=${testGroupPolicy}",
                "--cpi-name=testCpi",
                "--cpi-version=5.0.0.0-SNAPSHOT",
                "--file=$cpiOutputFile",
                "--keystore=${testKeyStore}",
                "--storepass=keystore password",
                "--key=${SIGNING_KEY_1_ALIAS}",
                "--sig-file=$CPI_SIGNER_NAME"
            )
        }

        assertFalse(cpiOutputFile.exists())
        assertTrue(outText.contains("java.lang.IllegalArgumentException: Cpb is invalid"))
        assertTrue(
            outText.contains(
                "net.corda.libs.packaging.core.exception.CordappManifestException: " +
                        "Manifest has invalid attribute \"Corda-CPB-Format\" value \"null\""
            )
        )
    }
}