package net.corda.sdk.packaging.signing

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.sdk.packaging.KeyStoreHelper
import net.corda.sdk.packaging.TestUtils
// TODO  move tests into SDK!!! Signing is covered in CreateCpiV2 test
import net.corda.utilities.write
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.absolutePathString

class SigningHelpersTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        const val SIGNED_CPB_NAME = "sign-cpb-test.cpb"
        const val KEYSTORE_ALIAS = "alias"
        const val KEYSTORE_PASSWORD = "password"

        lateinit var commonSignedCpb: Path
        lateinit var commonUnsignedCpb: Path

        private fun createTempFile(prefix: String): Path =
            Files.createTempFile(prefix, null).also { it.toFile().deleteOnExit() }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            commonSignedCpb = TestCpbV2Builder().signers(ALICE).build().toByteArray().let {
                createTempFile("unsignedCpb").write(it)
            }
            commonUnsignedCpb = TestCpbV2Builder().build().toByteArray().let {
                createTempFile("signedCpb").write(it)
            }
        }
    }

    @Test
    fun `sign adds manifest hashes related to signing`() {
        val (unsignedManifestMainAttributes, unsignedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(commonUnsignedCpb)

        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")

        // TODO define keystore in test setup
        val keyStoreFile = createTempFile("keyStore")
        KeyStoreHelper().generateKeyStore(keyStoreFile.toFile(), KEYSTORE_ALIAS, KEYSTORE_PASSWORD)
        val signingOptions = SigningOptions(
            keyStoreFile.absolutePathString(),
            KEYSTORE_ALIAS,
            KEYSTORE_PASSWORD
        )
        SigningHelpers.sign(commonUnsignedCpb, signedCpb, signingOptions)

        val (signedManifestMainAttributes, signedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(signedCpb)

        Assertions.assertEquals(signedManifestMainAttributes, unsignedManifestMainAttributes)
        Assertions.assertEquals(signedManifestEntries, unsignedManifestEntries)
        Assertions.assertTrue(signedManifestEntries.isNotEmpty())
        Assertions.assertTrue(
            signedManifestEntries.all {
                val entryKey = it.value
                entryKey.size == 1 && entryKey.containsKey(Attributes.Name("SHA-256-Digest"))
            }
        )
//        Assertions.assertTrue(removedSignaturesManifestEntries.isEmpty())
    }

    @Test
    fun `removeSignatures removes manifest hashes related to signing`() {
        val (signedManifestMainAttributes, signedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(commonSignedCpb)

        val removedSignaturesCpb = createTempFile("removedSignaturesCpb")
        SigningHelpers.removeSignatures(commonSignedCpb, removedSignaturesCpb)

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
        val signedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(commonSignedCpb)

        val removedSignaturesCpb = createTempFile("removedSignaturesCpb")
        SigningHelpers.removeSignatures(commonSignedCpb, removedSignaturesCpb)

        val clearedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(removedSignaturesCpb)

        Assertions.assertTrue(signedCpbSigningFiles.isNotEmpty())
        Assertions.assertTrue(clearedCpbSigningFiles.isEmpty())
    }
}