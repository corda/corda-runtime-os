package net.corda.libs.packaging.verify

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.ZipTweaker
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


// This is to avoid extracting the CPK archive in every single test case,
// no test case writes anything to the filesystem, nor alters the state of the test class instance;
// this makes it safe to use the same instance for all test cases (test case execution order is irrelevant)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JarVerifierTest {
    private lateinit var testDir: Path
    private lateinit var workflowCPKPath: Path
    private lateinit var processedWorkflowCPKPath: Path
    private lateinit var workflowCPK: Cpk
    private lateinit var trustedCerts: Set<Certificate>

    @Throws(Exception::class)
    fun getCertificate(certificatePath: String?): Certificate {
        val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        return FileInputStream(certificatePath).use {
            certificateFactory.generateCertificate(it)
        }
    }

    @BeforeAll
    fun setup(@TempDir junitTestDir: Path) {
        testDir = junitTestDir
        workflowCPKPath = Path.of(URI(System.getProperty("net.corda.packaging.test.workflow.cpk")))
        processedWorkflowCPKPath = testDir.resolve(workflowCPKPath.fileName)
        workflowCPK = Cpk.from(Files.newInputStream(workflowCPKPath), processedWorkflowCPKPath, workflowCPKPath.toString())
        trustedCerts = setOf(getCertificate(System.getProperty("net.corda.dev.certPath")))
    }

    @AfterAll
    fun teardown() {
        workflowCPK.close()
    }

    @Test
    fun `throws if CPK has no manifest`() {
        val outputStream = ByteArrayOutputStream()
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name == JarFile.MANIFEST_NAME) AfterTweakAction.DO_NOTHING
                else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }.run(Files.newInputStream(workflowCPKPath), outputStream)

        assertThrows<CordappManifestException> {
            val verifier = CpkVerifier(ByteArrayInputStream(outputStream.toByteArray()), trustedCerts)
            verifier.verify()
        }
    }

    @Test
    fun `throws if file added to CPK`() {
        val outputStream = ByteArrayOutputStream()
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) = if (currentEntry.name == workflowCPK.metadata.mainBundle) {
                outputStream.putNextEntry(ZipEntry("added_file.txt"))
                outputStream.write("test".toByteArray())
                outputStream.closeEntry()
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCPKPath), outputStream)

        assertThrows<SecurityException> {
            val verifier = CpkVerifier(ByteArrayInputStream(outputStream.toByteArray()), trustedCerts)
            verifier.verify()
        }
    }

    @Test
    fun `throws if file added to CPK and manifest`() {
        val outputStream = ByteArrayOutputStream()
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) = if (JarFile.MANIFEST_NAME.equals(currentEntry.name, ignoreCase = true)) {
                outputStream.putNextEntry(ZipEntry(JarFile.MANIFEST_NAME))
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    outputStream.write(buffer, 0, read)
                }
                outputStream.closeEntry()
                outputStream.putNextEntry(ZipEntry("added_file.txt"))
                outputStream.write("test".toByteArray())
                outputStream.closeEntry()
                AfterTweakAction.DO_NOTHING
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCPKPath), outputStream)

        assertThrows<SecurityException> {
            val verifier = CpkVerifier(ByteArrayInputStream(outputStream.toByteArray()), trustedCerts)
            verifier.verify()
        }
    }

    @Test
    fun `throws if file modified`() {
        val outputStream = ByteArrayOutputStream()
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) = if (currentEntry.name == "lib/config-1.4.1.jar") {
                outputStream.putNextEntry(ZipEntry(currentEntry.name))
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    buffer[0]++
                    outputStream.write(buffer, 0, read)
                }
                outputStream.closeEntry()
                AfterTweakAction.DO_NOTHING
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCPKPath), outputStream)

        assertThrows<SecurityException> {
            val verifier = CpkVerifier(ByteArrayInputStream(outputStream.toByteArray()), trustedCerts)
            verifier.verify()
        }
    }

    @Test
    fun `throws if archive is not signed`() {
        val outputStream = ByteArrayOutputStream()
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) = if (currentEntry.name.startsWith("META-INF/CORDAPP", ignoreCase = true)) {
                AfterTweakAction.DO_NOTHING
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCPKPath), outputStream)

        assertThrows<InvalidSignatureException> {
            val verifier = CpkVerifier(ByteArrayInputStream(outputStream.toByteArray()), trustedCerts)
            verifier.verify()
        }
    }

    @Test
    fun `throws if file deleted`() {
        val outputStream = ByteArrayOutputStream()
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) = if (currentEntry.name == workflowCPK.metadata.mainBundle) {
                AfterTweakAction.DO_NOTHING
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCPKPath), outputStream)

        assertThrows<SecurityException> {
            val verifier = CpkVerifier(ByteArrayInputStream(outputStream.toByteArray()), trustedCerts)
            verifier.verify()
        }
    }
}