package net.corda.cli.plugins.packaging

import java.nio.file.Path
import net.corda.cli.plugins.packaging.TestUtils.jarEntriesExistInCpx
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine

class CreateCpiV2Test {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        const val CPI_FILE_NAME = "output.cpi"
        const val SIGNER_NAME = "CPI-SIG"

        private val testGroupPolicy = Path.of(this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
            ?: error("TestGroupPolicy.json not found"))
        private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
            ?: error("signingkeys.pfx not found"))
    }

    @Test
    fun `cpi v2 contains cpb, manifest, signature files and GroupPolicy file`() {
        // build a signed CPB
        val createCpbTest = CreateCpbTest()
        createCpbTest.tempDir = tempDir
        createCpbTest.`packs CPKs into CPB`()
        val testCpb = Path.of("$tempDir/${CreateCpbTest.CREATED_CPB_NAME}")

        val outputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)
        CommandLine(CreateCpiV2()).execute (
            "--cpb=${testCpb}",
            "--group-policy=${testGroupPolicy}",
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
                    testCpb.fileName.toString()
                )
            )
        )
    }
}