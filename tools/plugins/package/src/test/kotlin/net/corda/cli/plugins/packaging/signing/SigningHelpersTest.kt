package net.corda.cli.plugins.packaging.signing

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import net.corda.cli.plugins.packaging.CreateCpbTest
import net.corda.cli.plugins.packaging.TestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SigningHelpersTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        const val SIGNED_CPB_NAME = "sign-cpb-test.cpb"

        @TempDir
        lateinit var commonTempDir: Path

        lateinit var signedCpb: Path

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // build a signed CPB
            val createCpbTest = CreateCpbTest()
            createCpbTest.tempDir = commonTempDir
            createCpbTest.`packs CPKs into CPB`()
            signedCpb = Path.of("$commonTempDir/${CreateCpbTest.CREATED_CPB_NAME}")
        }
    }

    @Test
    fun `removeSignatures removes manifest hashes related to signing`() {
        val (signedManifestMainAttributes, signedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(signedCpb)

        val removedSignaturesCpb = Path.of("$tempDir/${SIGNED_CPB_NAME}").also { Files.createFile(it) }
        SigningHelpers.removeSignatures(signedCpb, removedSignaturesCpb)

        val (removedSignaturesManifestMainAttributes, removedSignaturesManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(removedSignaturesCpb)

        Assertions.assertEquals(signedManifestMainAttributes, removedSignaturesManifestMainAttributes)
        Assertions.assertTrue(signedManifestEntries.isNotEmpty())
        Assertions.assertTrue(
            signedManifestEntries.all {
                val entryKey = it.value
                entryKey.size == 1 && entryKey.containsKey(Attributes.Name("SHA-256-Digest"))
            }
        )
        Assertions.assertTrue(removedSignaturesManifestEntries.isEmpty())
    }

    @Test
    fun `removeSignatures removes signing related files`() {
        val signedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(signedCpb)

        val removedSignaturesCpb = Path.of("$tempDir/${SIGNED_CPB_NAME}").also { Files.createFile(it) }
        SigningHelpers.removeSignatures(signedCpb, removedSignaturesCpb)

        val clearedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(removedSignaturesCpb)

        Assertions.assertTrue(signedCpbSigningFiles.isNotEmpty())
        Assertions.assertTrue(clearedCpbSigningFiles.isEmpty())
    }
}