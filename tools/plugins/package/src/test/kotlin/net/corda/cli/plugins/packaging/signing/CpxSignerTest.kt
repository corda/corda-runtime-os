package net.corda.cli.plugins.packaging.signing

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import net.corda.cli.plugins.packaging.CreateCpbTest
import net.corda.cli.plugins.packaging.TestUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CpxSignerTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        const val SIGNED_CPB_NAME = "sign-cpb-test.cpb"
    }

    @Test
    fun `removes manifest signatures`() {
        // build a signed CPB
        val createCpbTest = CreateCpbTest()
        createCpbTest.tempDir = tempDir
        createCpbTest.`packs CPKs into CPB`()
        val signedCpb = Path.of("$tempDir/${CreateCpbTest.CREATED_CPB_NAME}")

        val (signedManifestMainAttributes, signedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(signedCpb)

        val removedSignaturesCpb = Path.of("$tempDir/${SIGNED_CPB_NAME}").also { Files.createFile(it) }
        CpxSigner.removeSignatures(signedCpb, removedSignaturesCpb)

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
    fun `removes cpb signing files`() {
        // build a signed CPB
        val createCpbTest = CreateCpbTest()
        createCpbTest.tempDir = tempDir
        createCpbTest.`packs CPKs into CPB`()
        val signedCpb = Path.of("$tempDir/${CreateCpbTest.CREATED_CPB_NAME}")

        val signedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(signedCpb)

        val removedSignaturesCpb = Path.of("$tempDir/${SIGNED_CPB_NAME}").also { Files.createFile(it) }
        CpxSigner.removeSignatures(signedCpb, removedSignaturesCpb)

        val clearedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(removedSignaturesCpb)

        Assertions.assertTrue(signedCpbSigningFiles.isNotEmpty())
        Assertions.assertTrue(clearedCpbSigningFiles.isEmpty())
    }

}