package net.corda.sdk.packaging.signing

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.sdk.packaging.TestSigningKeys.CPB_SIGNER
import net.corda.sdk.packaging.TestSigningKeys.SIGNING_KEY_1_ALIAS
import net.corda.sdk.packaging.TestSigningKeys.SIGNING_KEY_2_ALIAS
import net.corda.sdk.packaging.TestUtils
import net.corda.sdk.packaging.TestUtils.jarEntriesContentIsEqualInCpxs
import net.corda.sdk.packaging.TestUtils.jarEntriesExistInCpx
import net.corda.utilities.write
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.absolutePathString

class SigningHelpersTest {
    @TempDir
    lateinit var tempDir: Path

    companion object {
        const val SIGNED_CPB_NAME = "sign-cpb-test.cpb"
        const val KEYSTORE_PASSWORD = "keystore password"

        const val CPx_SIGNER_NAME = "CPX-SIG"

        const val CPB_SIGNER_NAME = "CPB-SIG"
        const val SIG_NAME = CPB_SIGNER_NAME
        const val SIG_FILE_NAME = "META-INF/$SIG_NAME.SF"
        const val SIG_BLOCK_FILE_NAME = "META-INF/$SIG_NAME.EC"

        const val SIG_NAME_2 = "CPB-SIG2"
        const val SIG_FILE_NAME_2 = "META-INF/$SIG_NAME_2.SF"
        const val SIG_BLOCK_FILE_NAME_2 = "META-INF/$SIG_NAME_2.EC"

        lateinit var createdSignedCpb: Path
        lateinit var createdUnsignedCpb: Path

        private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
            ?: error("signingkeys.pfx not found"))

        private fun createTempFile(prefix: String): Path =
            Files.createTempFile(prefix, null).also { it.toFile().deleteOnExit() }

        private fun getManifestEntries(cpx: Path): Map<String,Attributes> {
            val (_, manifestEntries) = TestUtils.getManifestMainAttributesAndEntries(cpx)
            return manifestEntries
        }

        @JvmStatic
        @BeforeAll
        fun createCommonCpbs() {
            createdSignedCpb = TestCpbV2Builder()
                .signers(CPB_SIGNER)
                .build().toByteArray().let {
                createTempFile("signedCpb").write(it)
            }.also { assertTrue(getManifestEntries(it).isNotEmpty(), "Signed CPB should have non-empty manifest entries map") }
            createdUnsignedCpb = TestCpbV2Builder()
                .build().toByteArray().let {
                createTempFile("unsignedCpb").write(it)
            }.also { assertTrue(getManifestEntries(it).isEmpty(), "Unsigned CPB should have empty manifest entries map") }
        }
    }

    @Test
    fun `sign adds manifest hashes related to signing`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        SigningHelpers.sign(
            createdUnsignedCpb,
            signedCpb,
                SigningOptions(
                testKeyStore.toFile(),
                KEYSTORE_PASSWORD,
                SIGNING_KEY_1_ALIAS,
            )
        )

        val (unsignedManifestMainAttributes, _) =
            TestUtils.getManifestMainAttributesAndEntries(createdUnsignedCpb)
        val (signedManifestMainAttributes, signedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(signedCpb)

        assertEquals(signedManifestMainAttributes, unsignedManifestMainAttributes)
        assertTrue(signedManifestEntries.isNotEmpty())
        assertTrue(
            signedManifestEntries.values.all {
                it.size == 1 && it.containsKey(Attributes.Name("SHA-256-Digest"))
            },
            "All signed manifest entries should have only one key 'SHA-256-Digest', actual entries: $signedManifestEntries"
        )
    }

    @Test
    fun `sign unsigned cpb adds signatures and preserves content`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        SigningHelpers.sign(
            createdUnsignedCpb,
            signedCpb,
            SigningOptions(
                testKeyStore.toFile(),
                KEYSTORE_PASSWORD,
                SIGNING_KEY_1_ALIAS,
                null,
                CPx_SIGNER_NAME
            )
        )

        // check Manifest attributes and entries are same
        val (createdManifestMainAttributes, _) = TestUtils.getManifestMainAttributesAndEntries(createdUnsignedCpb)
        val (signedManifestMainAttributes, _) = TestUtils.getManifestMainAttributesAndEntries(signedCpb)
        assertEquals(createdManifestMainAttributes, signedManifestMainAttributes)

        val signedCpbSignatureRelatedFiles =
            (TestUtils.getSignatureBlockJarEntries(signedCpb).map { it.name } +
                    TestUtils.getSignatureJarEntries(signedCpb).map { it.name }).toSet()

        assertEquals(setOf("META-INF/$CPx_SIGNER_NAME.EC", "META-INF/$CPx_SIGNER_NAME.SF"), signedCpbSignatureRelatedFiles)

        val createdCpbEntries = TestUtils.getNonSignatureJarEntries(createdUnsignedCpb)
        val expectedJarEntriesNames = createdCpbEntries.map { it.name }
        assertTrue(
            expectedJarEntriesNames.all {
                jarEntriesContentIsEqualInCpxs(it to createdUnsignedCpb, it to signedCpb)
            },
            "Jar entries content should be equal in signed CPB as in the original unsigned CPB"
        )

        val signedCpbEntries = TestUtils.getNonSignatureJarEntries(signedCpb)
        assertTrue(signedCpbEntries.isNotEmpty())

        // A jar entry has 1 signer and 2 certificates
        val entry = signedCpbEntries.first()
        assertEquals(2, entry.certificates?.size)
        assertEquals(1, entry.codeSigners?.size)
    }

    @Test
    fun `sign an already signed cpb keeps previous signatures and adds new with pass in signature file name`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        SigningHelpers.sign(
            createdSignedCpb,
            signedCpb,
            SigningOptions(
                testKeyStore.toFile(),
                KEYSTORE_PASSWORD,
                SIGNING_KEY_2_ALIAS,
                null,
                SIG_NAME_2
            )
        )

        val createdCpbSignatureRelatedFiles =
            TestUtils.getSignatureBlockJarEntries(createdSignedCpb).map { it.name } +
                    TestUtils.getSignatureJarEntries(createdSignedCpb).map { it.name }
        val signedCpbSignatureRelatedFiles =
            TestUtils.getSignatureBlockJarEntries(signedCpb).map { it.name } +
                    TestUtils.getSignatureJarEntries(signedCpb).map { it.name }
        // assert all signature related files in created Cpb exist and are equal in signed Cpb
        assertTrue(
            createdCpbSignatureRelatedFiles.all { createdSignatureRelatedFile ->
                ((createdSignatureRelatedFile == SIG_FILE_NAME) ||
                        (createdSignatureRelatedFile == SIG_BLOCK_FILE_NAME)) &&
                        signedCpbSignatureRelatedFiles.contains(createdSignatureRelatedFile)
            }
        )
        assertTrue(
            createdCpbSignatureRelatedFiles.all { createdSignatureRelatedFile ->
                jarEntriesContentIsEqualInCpxs(
                    createdSignatureRelatedFile to createdSignedCpb,
                    createdSignatureRelatedFile to signedCpb
                )
            }
        )
        // assert we have a new signature in signed Cpb...
        val newSignatureFiles = signedCpbSignatureRelatedFiles - createdCpbSignatureRelatedFiles
        assertEquals(2, newSignatureFiles.size)
        // ... signature file
        assertTrue {
            val newSignatureFile = newSignatureFiles.single {
                it == SIG_FILE_NAME_2
            }
            jarEntriesContentIsEqualInCpxs(
                SIG_FILE_NAME to createdSignedCpb,
                newSignatureFile to signedCpb
            )
        }
        // ... signature block file
        assertTrue {
            val newSignatureBlockFile = newSignatureFiles.single {
                it == SIG_BLOCK_FILE_NAME_2
            }

            !jarEntriesContentIsEqualInCpxs(
                SIG_BLOCK_FILE_NAME to createdSignedCpb,
                newSignatureBlockFile to signedCpb
            )
        }

        val signedCpbEntries = TestUtils.getNonSignatureJarEntries(signedCpb)
        assertTrue(signedCpbEntries.isNotEmpty())

        // A jar entry has 2 signers and 4 certificates
        val entry = signedCpbEntries.first()
        assertEquals(4, entry.certificates?.size)
        assertEquals(2, entry.codeSigners?.size)
    }

    @Test
    fun `signing without specifying sig file option uses key alias`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        val signingOptions = SigningOptions(
            testKeyStore.toFile(),
            KEYSTORE_PASSWORD,
            SIGNING_KEY_2_ALIAS,
            null,
        )
        SigningHelpers.sign(createdUnsignedCpb, signedCpb, signingOptions)

        // Since sig file option is missing it will
        // use 8 first chars of key alias (i.e. "signing "), make it uppercase and replace not valid chars with '_'.
        assertTrue(
            jarEntriesExistInCpx(
                signedCpb,
                listOf(
                    "META-INF/SIGNING_.SF",
                    "META-INF/SIGNING_.EC",
                )
            )
        )
    }

    @Test
    fun `removeSignatures removes manifest hashes related to signing`() {
        val (signedManifestMainAttributes, signedManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(createdSignedCpb)

        val removedSignaturesCpb = createTempFile("removedSignaturesCpb")
        SigningHelpers.removeSignatures(createdSignedCpb, removedSignaturesCpb)

        val (removedSignaturesManifestMainAttributes, removedSignaturesManifestEntries) =
            TestUtils.getManifestMainAttributesAndEntries(removedSignaturesCpb)

        assertEquals(signedManifestMainAttributes, removedSignaturesManifestMainAttributes)
        assertTrue(
            signedManifestEntries.all {
                val entryKey = it.value
                entryKey.size == 1 && entryKey.containsKey(Attributes.Name("SHA-256-Digest"))
            },
        )
        assertTrue(removedSignaturesManifestEntries.isEmpty())
    }

    @Test
    fun `removeSignatures removes signing related files`() {
        val removedSignaturesCpb = createTempFile("removedSignaturesCpb")
        SigningHelpers.removeSignatures(createdSignedCpb, removedSignaturesCpb)

        val clearedCpbSigningFiles = TestUtils.getSignatureBlockJarEntries(removedSignaturesCpb)
        assertTrue(clearedCpbSigningFiles.isEmpty())
    }
}