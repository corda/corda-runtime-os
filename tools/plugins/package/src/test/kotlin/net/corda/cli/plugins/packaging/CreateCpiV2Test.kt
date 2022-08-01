package net.corda.cli.plugins.packaging

import java.nio.file.Path
import java.util.jar.Attributes
import net.corda.cli.plugins.packaging.TestUtils.assertContainsAllManifestAttributes
import net.corda.cli.plugins.packaging.TestUtils.jarEntriesExistInCpx
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
        lateinit var cpbDir: Path

        lateinit var cpbPath: Path

        const val CPI_FILE_NAME = "output.cpi"
        const val SIGNER_NAME = "CPI-SIG"

        private val testGroupPolicy = Path.of(this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
            ?: error("TestGroupPolicy.json not found"))
        private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
            ?: error("signingkeys.pfx not found"))

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Create a single cpb to be used for all tests
            val createCpbTest = CreateCpbTest()
            createCpbTest.tempDir = cpbDir
            createCpbTest.`packs CPKs into CPB`()
            cpbPath = Path.of("$cpbDir/${CreateCpbTest.CREATED_CPB_NAME}")
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
            "--key=signing key 1",
            "--sig-file=$SIGNER_NAME"
        )

        assertTrue(
            jarEntriesExistInCpx(
                outputFile,
                listOf(
                    "META-INF/MANIFEST.MF",
                    "META-INF/$SIGNER_NAME.SF",
                    "META-INF/$SIGNER_NAME.RSA",
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
            "--key=signing key 1",
            "--sig-file=$SIGNER_NAME"
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
}