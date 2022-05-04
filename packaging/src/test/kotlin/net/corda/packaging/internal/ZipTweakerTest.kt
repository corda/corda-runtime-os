package net.corda.packaging.internal

import net.corda.packaging.CPK
import net.corda.packaging.util.ZipTweaker
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.OutputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream
import java.util.zip.ZipInputStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZipTweakerTest {

    private lateinit var testDir : Path
    private lateinit var workflowCPKPath : URL

    @BeforeAll
    fun setup(@TempDir junitTestDir : Path) {
        testDir = junitTestDir
        workflowCPKPath = URI(System.getProperty("net.corda.packaging.test.workflow.cpk")).toURL()
    }

    @Test
    @Suppress("ComplexMethod")
    fun `Ensure a jar with removed signature can be read`() {
        CPK.from(workflowCPKPath.openStream(), testDir).use { cpk ->
            val algo = "SHA-256"
            val originalEntries = ZipInputStream(cpk.getResourceAsStream(cpk.metadata.mainBundle)).use { zipInputStream ->
                generateSequence { zipInputStream.nextEntry }.map {
                    it.name to let {
                        val md = MessageDigest.getInstance(algo)
                        DigestInputStream(zipInputStream, md).copyTo(OutputStream.nullOutputStream())
                        SecureHash(algo, md.digest())
                    }
                }.filter {
                    !it.first.uppercase().endsWith(".SF")
                }.associate { it }
            }
            val dest = testDir.resolve("mainBundle.jar")
            Files.newOutputStream(testDir.resolve("mainBundle.jar")).use { outputStream ->
                cpk.getResourceAsStream(cpk.metadata.mainBundle).use {
                    it.transferTo(outputStream)
                }
            }
            ZipTweaker.removeJarSignature(dest)
            JarInputStream(Files.newInputStream(dest), true).use { jarInputStream ->
                while(true) {
                    val jarEntry = jarInputStream.nextJarEntry ?: break
                    Assertions.assertNull(jarEntry.codeSigners)
                    jarInputStream.copyTo(OutputStream.nullOutputStream())
                }
            }
            val unsignedJarEntries = ZipInputStream(cpk.getResourceAsStream(cpk.metadata.mainBundle)).use { zipInputStream ->
                generateSequence { zipInputStream.nextEntry }.map {
                    it.name to let {
                        val md = MessageDigest.getInstance(algo)
                        DigestInputStream(zipInputStream, md).copyTo(OutputStream.nullOutputStream())
                        SecureHash(algo, md.digest())
                    }
                }.filter {
                    !it.first.uppercase().endsWith(".SF")
                }.associate { it }
            }

            Assertions.assertEquals(originalEntries, unsignedJarEntries)
        }
    }
}
