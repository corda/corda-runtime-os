package net.corda.cli.plugins.packaging

import java.nio.file.Path
import net.corda.cli.plugins.packaging.CreateCpbTest.Companion.CPB_SIGNER_NAME
import net.corda.cli.plugins.packaging.TestUtils.jarEntriesContentIsEqualInCpxs
import net.corda.cli.plugins.packaging.TestUtils.jarEntriesExistInCpx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine

class SignCpbTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var app: SignCpx

    private companion object {
        // Share cpb across all tests since we only read it and not modify it to save disk writes
        @TempDir
        lateinit var cpbDir: Path

        lateinit var createdCpb: Path

        const val CPx_SIGNER_NAME = "CPX-SIG"

        const val SIGNED_CPB_NAME = "signed-cpb-output.cpb"

        const val SIG_NAME = CPB_SIGNER_NAME
        const val SIG_FILE_NAME = "META-INF/$SIG_NAME.SF"
        const val SIG_BLOCK_FILE_NAME = "META-INF/$SIG_NAME.RSA"

        const val SIG_NAME_2 = "CPB-SIG2"
        const val SIG_FILE_NAME_2 = "META-INF/$SIG_NAME_2.SF"
        const val SIG_BLOCK_FILE_NAME_2 = "META-INF/$SIG_NAME_2.RSA"

        private val testKeyStore = Path.of(
            this::class.java.getResource("/signingkeys.pfx")?.toURI()
                ?: error("signingkeys.pfx not found")
        )

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Create a single cpb to be used for all tests
            val createCpbTest = CreateCpbTest()
            createCpbTest.tempDir = cpbDir
            createCpbTest.`packs CPKs into CPB`()
            createdCpb = Path.of("${cpbDir}/${CreateCpbTest.CREATED_CPB_NAME}")
        }
    }

    @Test
    fun `sign cpb with erasing signatures removes previous signatures and adds new with the same signature file name`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        app = SignCpx()
        CommandLine(app).execute(
            createdCpb.toString(),
            "--file=$signedCpb",
            "--keystore=${testKeyStore}",
            "--storepass=keystore password",
            "--key=signing key 2",
            "--sig-file=$CPx_SIGNER_NAME"
        )

        // check Manifest attributes and entries are same
        val (createdManifestMainAttributes, createdManifestEntries) = TestUtils.getManifestMainAttributesAndEntries(createdCpb)
        val (signedManifestMainAttributes, signedManifestEntries) = TestUtils.getManifestMainAttributesAndEntries(signedCpb)
        assertEquals(createdManifestMainAttributes, signedManifestMainAttributes)
        assertEquals(createdManifestEntries, signedManifestEntries)

        val createdCpbSignatureRelatedFiles =
            (TestUtils.getSignatureBlockJarEntries(createdCpb).map { it.name } +
                    TestUtils.getSignatureJarEntries(createdCpb).map { it.name }).toSet()
        val signedCpbSignatureRelatedFiles =
            (TestUtils.getSignatureBlockJarEntries(signedCpb).map { it.name } +
                    TestUtils.getSignatureJarEntries(signedCpb).map { it.name }).toSet()

        assertEquals(setOf(SIG_FILE_NAME, SIG_BLOCK_FILE_NAME), createdCpbSignatureRelatedFiles)
        assertEquals(setOf("META-INF/$CPx_SIGNER_NAME.RSA", "META-INF/$CPx_SIGNER_NAME.SF"), signedCpbSignatureRelatedFiles)

        // check signature file contents are same
        assertTrue {
            val createdSigFile = createdCpbSignatureRelatedFiles.single {
                it == SIG_FILE_NAME
            }

            val signedSigFile = signedCpbSignatureRelatedFiles.single {
                it == "META-INF/$CPx_SIGNER_NAME.SF"
            }

            jarEntriesContentIsEqualInCpxs(
                createdSigFile to createdCpb,
                signedSigFile to signedCpb
            )
        }

        // check signature block file contents are not same (different keys)
        assertTrue {
            val createdSigBlockFile = createdCpbSignatureRelatedFiles.single {
                it == SIG_BLOCK_FILE_NAME
            }

            val signedSigBlockFile = signedCpbSignatureRelatedFiles.single {
                it == "META-INF/$CPx_SIGNER_NAME.RSA"
            }

            !jarEntriesContentIsEqualInCpxs(
                createdSigBlockFile to createdCpb,
                signedSigBlockFile to signedCpb
            )
        }
    }

    @Test
    fun `sign cpb without erasing signatures keeps previous signatures and adds new with pass in signature file name`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        app = SignCpx()
        CommandLine(app).execute(
            createdCpb.toString(),
            "--multiple-signatures=true",
            "--file=$signedCpb",
            "--keystore=${testKeyStore}",
            "--storepass=keystore password",
            "--key=signing key 2",
            "--sig-file=$SIG_NAME_2",
        )

        val createdCpbSignatureRelatedFiles =
            TestUtils.getSignatureBlockJarEntries(createdCpb).map { it.name } +
                    TestUtils.getSignatureJarEntries(createdCpb).map { it.name }
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
                    createdSignatureRelatedFile to createdCpb,
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
                SIG_FILE_NAME to createdCpb,
                newSignatureFile to signedCpb
            )
        }
        // ... signature block file
        assertTrue {
            val newSignatureBlockFile = newSignatureFiles.single {
                it == SIG_BLOCK_FILE_NAME_2
            }

            !jarEntriesContentIsEqualInCpxs(
                SIG_BLOCK_FILE_NAME to createdCpb,
                newSignatureBlockFile to signedCpb
            )
        }
    }

    @Test
    fun `signing without specifying sig file option uses key alias`() {
        val signedCpb = Path.of("$tempDir/$SIGNED_CPB_NAME")
        app = SignCpx()
        CommandLine(app).execute(
            createdCpb.toString(),
            "--multiple-signatures=true",
            "--file=$signedCpb",
            "--keystore=${testKeyStore}",
            "--storepass=keystore password",
            "--key=signing key 2"
        )

        // Since sig file option is missing it will
        // use 8 first chars of key alias (i.e. "signing "), make it uppercase and replace not valid chars with '_'.
        assertTrue(
            jarEntriesExistInCpx(
                signedCpb,
                listOf(
                    "META-INF/SIGNING_.SF",
                    "META-INF/SIGNING_.RSA",
                )
            )
        )

    }
}