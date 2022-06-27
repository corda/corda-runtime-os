package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.JarReader
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import net.corda.test.util.InMemoryZipFile
import net.corda.libs.packaging.verify.TestUtils.ALICE
import net.corda.libs.packaging.verify.TestUtils.BOB
import net.corda.libs.packaging.verify.TestUtils.ROOT_CA
import net.corda.libs.packaging.verify.TestUtils.signedBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.BufferedReader

class CpkV2VerifierTest {
    private fun verify(cpk: InMemoryZipFile) {
        cpk.use {
            CpkV2Verifier(JarReader("test.cpk", cpk.inputStream(), setOf(ROOT_CA))).verify()
        }
    }

    @Test
    fun `successfully verifies valid CPK`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        assertDoesNotThrow {
            verify(cpk)
        }
    }

    @Test
    fun `throws if CPK not signed`() {
        val cpk = TestCpkV2Builder().build()

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpk)
        }
        assertEquals("File META-INF/CPKDependencies is not signed in package \"test.cpk\"", exception.message)
    }

    @Test
    fun `throws if CPK has no manifest`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        cpk.deleteEntry("META-INF/MANIFEST.MF")

        val exception = assertThrows<CordappManifestException> {
            verify(cpk)
        }
        assertEquals("Manifest file is missing or is not the first entry in package \"test.cpk\"", exception.message)
    }

    @Test
    fun `throws if entry deleted from Manifest`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        val manifest = cpk.getManifest()
        val removedEntry = manifest.entries.remove("META-INF/privatelib/library1.jar")
        cpk.setManifest(manifest)

        assertNotNull(removedEntry)
        val exception = assertThrows<SecurityException> {
            verify(cpk)
        }
        assertEquals("no manifest section for signature file entry META-INF/privatelib/library1.jar", exception.message)
    }

    @Test
    fun `throws if entry deleted from signature file`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        // Delete from signature file
        val signatureFileEntry = cpk.getEntry("META-INF/ALICE.SF")
        val signatureFile = cpk.getInputStream(signatureFileEntry).bufferedReader().use(BufferedReader::readText)
        val newSignatureFile = signatureFile
            .split("\\R{2}".toRegex())
            .filter { !it.startsWith("Name: META-INF/privatelib/library1.jar") }
            .joinToString(separator = "\n\n")
        cpk.updateEntry(signatureFileEntry.name, newSignatureFile.toByteArray())

        assertNotEquals(signatureFile, newSignatureFile)
        val exception = assertThrows<SecurityException> {
            verify(cpk)
        }
        assertEquals("cannot verify signature block file META-INF/ALICE", exception.message)
    }

    @Test
    fun `throws if entry deleted from one of multiple signature files`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE, BOB)
            .build()

        // Delete from signature file
        val signatureFileEntry = cpk.getEntry("META-INF/ALICE.SF")
        val signatureFile = cpk.getInputStream(signatureFileEntry).bufferedReader().use(BufferedReader::readText)
        val newSignatureFile = signatureFile
            .split("\\R{2}".toRegex())
            .filter { !it.startsWith("Name: META-INF/privatelib/library1.jar") }
            .joinToString(separator = "\n\n")
        cpk.updateEntry(signatureFileEntry.name, newSignatureFile.toByteArray())

        assertNotEquals(signatureFile, newSignatureFile)
        val exception = assertThrows<SecurityException> {
            verify(cpk)
        }
        assertEquals("cannot verify signature block file META-INF/ALICE", exception.message)
    }

    @Test
    fun `throws if file modified in CPK`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        cpk.updateEntry("META-INF/privatelib/library1.jar", "modified".toByteArray())

        val exception = assertThrows<SecurityException> {
            verify(cpk)
        }
        assertEquals("SHA-256 digest error for META-INF/privatelib/library1.jar", exception.message)
    }

    @Test
    fun `throws if unsigned file added to CPK`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        cpk.addEntry("added_file.txt", "added".toByteArray())

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpk)
        }
        assertEquals("File added_file.txt is not signed in package \"test.cpk\"", exception.message)
    }

    @Test
    fun `throws if signed file added to CPK`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        cpk.addEntry("added_file.txt", "test".toByteArray())
        val cpk2 = InMemoryZipFile(cpk.inputStream()).signedBy(BOB)

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpk2)
        }
        assertTrue(exception.message!!.startsWith("Mismatch between signers"))
    }

    @Test
    fun `throws if file deleted in CPK`() {
        val cpk = TestCpkV2Builder()
            .signers(ALICE)
            .build()

        cpk.deleteEntry("META-INF/privatelib/library1.jar")

        val exception = assertThrows<SecurityException> {
            verify(cpk)
        }
        assertEquals("Manifest entry found for missing file META-INF/privatelib/library1.jar in package \"test.cpk\"", exception.message)
    }
}
