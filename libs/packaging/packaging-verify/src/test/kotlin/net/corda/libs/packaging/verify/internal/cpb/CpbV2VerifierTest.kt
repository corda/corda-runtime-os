package net.corda.libs.packaging.verify.internal.cpb

import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.DependencyResolutionException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.TestUtils.BOB
import net.corda.libs.packaging.testutils.TestUtils.ROOT_CA
import net.corda.libs.packaging.testutils.TestUtils.addFile
import net.corda.libs.packaging.testutils.TestUtils.base64ToBytes
import net.corda.libs.packaging.testutils.TestUtils.signedBy
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import net.corda.libs.packaging.testutils.cpk.TestCpkV2Builder
import net.corda.libs.packaging.verify.JarReader
import net.corda.test.util.InMemoryZipFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

class CpbV2VerifierTest {
    private fun verify(cpb: InMemoryZipFile) {
        cpb.use {
            CpbV2Verifier(JarReader("test.cpb", cpb.inputStream(), setOf(ROOT_CA))).verify()
        }
    }

    @Test
    fun `successfully verifies valid CPB`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        assertDoesNotThrow {
            verify(cpb)
        }
    }

    @Test
    fun `throws if CPB not signed`() {
        val cpb = TestCpbV2Builder().build()

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpb)
        }
        assertEquals("File testCpk1-1.0.0.0.jar is not signed in package \"test.cpb\"", exception.message)
    }

    @Test
    fun `throws if CPB has no manifest`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        cpb.deleteEntry("META-INF/MANIFEST.MF")

        val exception = assertThrows<CordappManifestException> {
            verify(cpb)
        }
        assertEquals("Manifest file is missing or is not the first entry in package \"test.cpb\"", exception.message)
    }

    @Test
    fun `throws if entry deleted from Manifest`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        val manifest = cpb.getManifest()
        val removedEntry = manifest.entries.remove("testCpk1-1.0.0.0.jar")
        cpb.setManifest(manifest)

        assertNotNull(removedEntry)
        val exception = assertThrows<SecurityException> {
            verify(cpb)
        }
        assertEquals("no manifest section for signature file entry testCpk1-1.0.0.0.jar", exception.message)
    }

    @Test
    fun `throws if entry deleted from signature file`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        // Delete from signature file
        val signatureFileEntry = cpb.getEntry("META-INF/ALICE.SF")
        val signatureFile = cpb.getInputStream(signatureFileEntry).bufferedReader().use(BufferedReader::readText)
        val newSignatureFile = signatureFile
            .split("\\R{2}".toRegex())
            .filter { !it.startsWith("Name: lib/library1.jar") }
            .joinToString(separator = "\n\n")
        cpb.updateEntry(signatureFileEntry.name, newSignatureFile.toByteArray())

        assertNotEquals(signatureFile, newSignatureFile)
        val exception = assertThrows<SecurityException> {
            verify(cpb)
        }
        assertEquals("cannot verify signature block file META-INF/ALICE", exception.message)
    }

    @Test
    fun `throws if entry deleted from one of multiple signature files`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE, BOB)
            .build()

        // Delete from signature file
        val signatureFileEntry = cpb.getEntry("META-INF/ALICE.SF")
        val signatureFile = cpb.getInputStream(signatureFileEntry).bufferedReader().use(BufferedReader::readText)
        val newSignatureFile = signatureFile
            .split("\\R{2}".toRegex())
            .filter { !it.startsWith("Name: lib/library1.jar") }
            .joinToString(separator = "\n\n")
        cpb.updateEntry(signatureFileEntry.name, newSignatureFile.toByteArray())

        assertNotEquals(signatureFile, newSignatureFile)
        val exception = assertThrows<SecurityException> {
            verify(cpb)
        }
        assertEquals("cannot verify signature block file META-INF/ALICE", exception.message)
    }

    @Test
    fun `throws if file modified in CPB`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        cpb.updateEntry("testCpk1-1.0.0.0.jar", "modified".toByteArray())

        val exception = assertThrows<SecurityException> {
            verify(cpb)
        }
        assertEquals("SHA-256 digest error for testCpk1-1.0.0.0.jar", exception.message)
    }

    @Test
    fun `throws if unsigned file added to CPB`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        cpb.addEntry("added_file.txt", "added".toByteArray())

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpb)
        }
        assertEquals("File added_file.txt is not signed in package \"test.cpb\"", exception.message)
    }

    @Test
    fun `throws if signed file added to CPB`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        cpb.addEntry("added_file.txt", "test".toByteArray())
        val cpb2 = InMemoryZipFile(cpb.inputStream()).signedBy(BOB)

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpb2)
        }
        assertTrue(exception.message!!.startsWith("Mismatch between signers"))
    }

    @Test
    fun `throws if file deleted in CPB`() {
        val cpb = TestCpbV2Builder()
            .signers(ALICE)
            .build()

        cpb.deleteEntry("testCpk1-1.0.0.0.jar")

        val exception = assertThrows<SecurityException> {
            verify(cpb)
        }
        assertEquals("Manifest entry found for missing file testCpk1-1.0.0.0.jar in package \"test.cpb\"", exception.message)
    }

    @Test
    fun `throws if CPK signer dependency not satisfied (missing CPK)`() {
        val cpb = TestCpbV2Builder()
            .cpks(TestCpkV2Builder().dependencies(TestUtils.Dependency("notExisting.jar", "1.0.0.0")))
            .signers(ALICE)
            .build()

        assertThrows<DependencyResolutionException> {
            verify(cpb)
        }
    }

    @Test
    fun `throws if CPK signer dependency not satisfied (different version)`() {
        val cpb = TestCpbV2Builder()
            .cpks(
                TestCpkV2Builder()
                    .name("test-1.0.0.0.jar")
                    .bundleName("test.cpk")
                    .bundleVersion("2.0.0.0")
                    .dependencies(TestUtils.Dependency("dependency.cpk", "1.0.0.0")),
                TestCpkV2Builder()
                    .name("dependency-1.1.0.0.jar")
                    .bundleName("dependency.cpk")
                    .bundleVersion("1.1.0.0"))
            .signers(ALICE)
            .build()

        assertThrows<DependencyResolutionException> {
            verify(cpb)
        }
    }

    @Test
    fun `throws if CPK signer dependency not satisfied (different signer)`() {
        val cpb = TestCpbV2Builder()
            .cpks(
                TestCpkV2Builder()
                    .name("test-1.0.0.0.jar")
                    .bundleName("test.cpk")
                    .bundleVersion("2.0.0.0")
                    .dependencies(TestUtils.Dependency("dependency.cpk", "1.0.0.0"))
                    .signers(ALICE),
                TestCpkV2Builder()
                    .name("dependency-1.0.0.0.jar")
                    .bundleName("dependency.cpk")
                    .bundleVersion("1.0.0.0")
                    .signers(BOB))
            .signers(ALICE)
            .build()

        assertThrows<DependencyResolutionException> {
            verify(cpb)
        }
    }

    @Test
    fun `throws if CPK hash dependency not satisfied (invalid hash)`() {
        val cpb = TestCpbV2Builder()
            .cpks(
                TestCpkV2Builder()
                    .name("test-1.0.0.0.jar")
                    .bundleName("test.cpk")
                    .bundleVersion("2.0.0.0")
                    .dependencies(
                        TestUtils.Dependency(
                            "dependency.cpk",
                            "1.0.0.0",
                            SecureHashImpl("SHA-256", base64ToBytes("qlnYKfLKj931q+pA2BX5N+PlTlcrZbk7XCFq5llOfWs="))
                        )),
                TestCpkV2Builder()
                    .name("dependency-1.0.0.0.jar")
                    .bundleName("dependency.cpk")
                    .bundleVersion("1.0.0.0"))
            .signers(ALICE)
            .build()

        assertThrows<DependencyResolutionException> {
            verify(cpb)
        }
    }

    @Test
    fun `successfully verifies valid CPK hash dependency`() {
        val dependencyCpk = TestCpkV2Builder()
            .name("dependency-1.0.0.0.jar")
            .bundleName("dependency.cpk")
            .bundleVersion("1.0.0.0")
            .signers(BOB)
            .build()
            .toByteArray()

        val dependencyCpkHash = run {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(ByteArrayInputStream(dependencyCpk), digest).use {
                it.readAllBytes()
            }
            SecureHashImpl("SHA-256", digest.digest())
        }

        val cpb = TestCpbV2Builder()
            .cpks(
                TestCpkV2Builder()
                    .name("test-1.0.0.0.jar")
                    .bundleName("test.cpk")
                    .bundleVersion("2.0.0.0")
                    .dependencies(
                        TestUtils.Dependency("dependency.cpk", "1.0.0.0", dependencyCpkHash))
                    .signers(ALICE))
            .build()
            .apply { addFile("dependency-1.0.0.0.jar", dependencyCpk) }
            .signedBy(ALICE)

        assertDoesNotThrow {
            verify(cpb)
        }
    }
}
