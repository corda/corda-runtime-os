package net.corda.sdk.packaging

import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.libs.packaging.testutils.cpk.TestCpkV2Builder
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.sdk.packaging.CpiV2Creator.CPI_FORMAT_ATTRIBUTE
import net.corda.sdk.packaging.CpiV2Creator.CPI_FORMAT_ATTRIBUTE_NAME
import net.corda.sdk.packaging.CpiV2Creator.CPI_NAME_ATTRIBUTE_NAME
import net.corda.sdk.packaging.CpiV2Creator.CPI_UPGRADE_ATTRIBUTE_NAME
import net.corda.sdk.packaging.CpiV2Creator.CPI_VERSION_ATTRIBUTE_NAME
import net.corda.sdk.packaging.TestSigningKeys.CPB_SIGNER
import net.corda.sdk.packaging.TestSigningKeys.CPI_SIGNER_NAME
import net.corda.sdk.packaging.TestSigningKeys.CPK_SIGNER
import net.corda.sdk.packaging.TestSigningKeys.SIGNING_KEY_1_ALIAS
import net.corda.sdk.packaging.TestUtils.assertContainsAllManifestAttributes
import net.corda.sdk.packaging.TestUtils.jarEntriesExistInCpx
import net.corda.sdk.packaging.signing.SigningOptions
import net.corda.utilities.exists
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.Attributes
import kotlin.io.path.readText
import kotlin.test.assertNotNull

class CpiV2CreatorTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {

        // Share cpb across all tests since we only read it and not modify it to save disk writes
        @TempDir
        lateinit var commonTempDir: Path

        lateinit var cpbPath: Path

        const val CPI_FILE_NAME = "output.cpi"
        const val KEYSTORE_PASSWORD = "keystore password"

        private val testGroupPolicy = Path.of(
            this::class.java.getResource("/TestGroupPolicy.json")?.toURI()
                ?: error("TestGroupPolicy.json not found")
        )
        private val invalidTestGroupPolicy = Path.of(
            this::class.java.getResource("/InvalidTestGroupPolicy.json")?.toURI()
                ?: error("InvalidTestGroupPolicy.json not found")
        )
        private val testKeyStore = Path.of(
            this::class.java.getResource("/signingkeys.pfx")?.toURI()
                ?: error("signingkeys.pfx not found")
        )

        private val signingOptions = SigningOptions(
            testKeyStore,
            KEYSTORE_PASSWORD,
            SIGNING_KEY_1_ALIAS,
            null,
            CPI_SIGNER_NAME
        )

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
        CpiV2Creator.createCpi(
            cpbPath,
            outputFile,
            testGroupPolicy.readText(),
            CpiAttributes("testCpi", "5.0.0.0-SNAPSHOT"),
            signingOptions
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
        CpiV2Creator.createCpi(
            cpbPath,
            cpiOutputFile,
            testGroupPolicy.readText(),
            CpiAttributes("testCpi", "5.0.0.0-SNAPSHOT"),
            signingOptions
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
    fun `cpi v2 can create CPI with only Group Policy and no CPB`() {
        val cpiOutputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)
        assertDoesNotThrow {
            CpiV2Creator.createCpi(
                cpbPath,
                cpiOutputFile,
                testGroupPolicy.readText(),
                CpiAttributes("testCpi", "5.0.0.0-SNAPSHOT"),
                signingOptions
            )
        }

        assertTrue(
            jarEntriesExistInCpx(
                cpiOutputFile,
                listOf(
                    "META-INF/MANIFEST.MF",
                    "META-INF/$CPI_SIGNER_NAME.SF",
                    "META-INF/$CPI_SIGNER_NAME.EC",
                    "META-INF/GroupPolicy.json",
                )
            )
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
    fun `createCpi aborts if its not a cpb before packing it into a cpi`() {
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

        val error = assertThrows<IllegalArgumentException> {
            CpiV2Creator.createCpi(
                cpkPath,
                cpiOutputFile,
                testGroupPolicy.readText(),
                CpiAttributes("testCpi", "5.0.0.0-SNAPSHOT"),
                signingOptions
            )
        }
        assertFalse(cpiOutputFile.exists())
        assertNotNull(error.message)
        assertTrue(error.message!!.contains("Error verifying CPB"))
    }

    @Test
    fun `cpi create tool aborts if its group policy is invalid`() {
        val outputFile = Path.of(tempDir.toString(), CPI_FILE_NAME)

        assertThrows<MembershipSchemaValidationException> {
            CpiV2Creator.createCpi(
                cpbPath,
                outputFile,
                invalidTestGroupPolicy.readText(),
                CpiAttributes("testCpi", "5.0.0.0-SNAPSHOT"),
                signingOptions
            )
        }
        assertFalse(outputFile.exists())
    }
}
