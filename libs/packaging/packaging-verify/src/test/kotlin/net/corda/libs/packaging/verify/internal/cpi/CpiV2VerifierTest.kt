package net.corda.libs.packaging.verify.internal.cpi

import net.corda.libs.packaging.JarReader
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import net.corda.test.util.InMemoryZipFile
import net.corda.libs.packaging.verify.TestUtils.ALICE
import net.corda.libs.packaging.verify.TestUtils.BOB
import net.corda.libs.packaging.verify.TestUtils.ROOT_CA
import net.corda.libs.packaging.verify.TestUtils.signedBy
import net.corda.libs.packaging.verify.internal.cpi.TestCpiV2Builder.Companion.POLICY_FILE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.BufferedReader

class CpiV2VerifierTest {
    private fun verify(cpi: InMemoryZipFile) {
        cpi.use {
            CpiV2Verifier(JarReader("test.cpi", cpi.inputStream(), setOf(ROOT_CA))).verify()
        }
    }

    @Test
    fun `successfully verifies valid CPI`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        assertDoesNotThrow {
            verify(cpi)
        }
    }

    @Test
    fun `throws if CPI not signed`() {
        val cpi = TestCpiV2Builder().build()

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpi)
        }
        assertEquals("File $POLICY_FILE is not signed in package \"test.cpi\"", exception.message)
    }

    @Test
    fun `throws if CPI has no manifest`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        cpi.deleteEntry("META-INF/MANIFEST.MF")

        val exception = assertThrows<CordappManifestException> {
            verify(cpi)
        }
        assertEquals("Manifest file is missing or is not the first entry in package \"test.cpi\"", exception.message)
    }

    @Test
    fun `throws if entry deleted from Manifest`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        val manifest = cpi.getManifest()
        val removedEntry = manifest.entries.remove("testCpbV2.cpb")
        cpi.setManifest(manifest)

        assertNotNull(removedEntry)
        val exception = assertThrows<SecurityException> {
            verify(cpi)
        }
        assertEquals("no manifest section for signature file entry testCpbV2.cpb", exception.message)
    }

    @Test
    fun `throws if entry deleted from signature file`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        // Delete from signature file
        val signatureFileEntry = cpi.getEntry("META-INF/ALICE.SF")
        val signatureFile = cpi.getInputStream(signatureFileEntry).bufferedReader().use(BufferedReader::readText)
        val newSignatureFile = signatureFile
            .split("\\R{2}".toRegex())
            .filter { !it.startsWith("Name: lib/library1.jar") }
            .joinToString(separator = "\n\n")
        cpi.updateEntry(signatureFileEntry.name, newSignatureFile.toByteArray())

        assertNotEquals(signatureFile, newSignatureFile)
        val exception = assertThrows<SecurityException> {
            verify(cpi)
        }
        assertEquals("cannot verify signature block file META-INF/ALICE", exception.message)
    }

    @Test
    fun `throws if entry deleted from one of multiple signature files`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE, BOB)
            .build()

        // Delete from signature file
        val signatureFileEntry = cpi.getEntry("META-INF/ALICE.SF")
        val signatureFile = cpi.getInputStream(signatureFileEntry).bufferedReader().use(BufferedReader::readText)
        val newSignatureFile = signatureFile
            .split("\\R{2}".toRegex())
            .filter { !it.startsWith("Name: lib/library1.jar") }
            .joinToString(separator = "\n\n")
        cpi.updateEntry(signatureFileEntry.name, newSignatureFile.toByteArray())

        assertNotEquals(signatureFile, newSignatureFile)
        val exception = assertThrows<SecurityException> {
            verify(cpi)
        }
        assertEquals("cannot verify signature block file META-INF/ALICE", exception.message)
    }

    @Test
    fun `throws if file modified in CPI`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        cpi.updateEntry("testCpbV2.cpb", "modified".toByteArray())

        val exception = assertThrows<SecurityException> {
            verify(cpi)
        }
        assertEquals("SHA-256 digest error for testCpbV2.cpb", exception.message)
    }

    @Test
    fun `throws if unsigned file added to CPI`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        cpi.addEntry("added_file.txt", "added".toByteArray())

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpi)
        }
        assertEquals("File added_file.txt is not signed in package \"test.cpi\"", exception.message)
    }

    @Test
    fun `throws if signed file added to CPI`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        cpi.addEntry("added_file.txt", "test".toByteArray())
        val cpi2 = InMemoryZipFile(cpi.inputStream()).signedBy(BOB)

        val exception = assertThrows<InvalidSignatureException> {
            verify(cpi2)
        }
        assertTrue(exception.message!!.startsWith("Mismatch between signers"))
    }

    @Test
    fun `throws if file deleted in CPI`() {
        val cpi = TestCpiV2Builder()
            .signers(ALICE)
            .build()

        cpi.deleteEntry("testCpbV2.cpb")

        val exception = assertThrows<SecurityException> {
            verify(cpi)
        }
        assertEquals("Manifest entry found for missing file testCpbV2.cpb in package \"test.cpi\"", exception.message)
    }
}
