package net.corda.packaging.internal

import net.corda.packaging.Cpk
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
    private lateinit var workflowCpkPath : URL

    @BeforeAll
    fun setup(@TempDir junitTestDir : Path) {
        testDir = junitTestDir
        workflowCpkPath = URI(System.getProperty("net.corda.packaging.test.workflow.cpk")).toURL()
    }

    @Test
    fun `Ensure a jar with removed signature can be read`() {
        val cpk = Cpk.Expanded.from(workflowCpkPath.openStream(), testDir)
        val algo = "SHA-256"
        val originalEntries = ZipInputStream(Files.newInputStream(cpk.mainJar)).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }.map {
                it.name to let {
                    val md = MessageDigest.getInstance(algo)
                    DigestInputStream(zipInputStream, md).copyTo(OutputStream.nullOutputStream())
                    SecureHash(algo, md.digest())
                }
            }.filter {
                !it.first.toUpperCase().endsWith(".SF")
            }.associate { it }
        }
        ZipTweaker.removeJarSignature(cpk.mainJar)
        JarInputStream(Files.newInputStream(cpk.mainJar), true).use { jarInputStream ->
            while(true) {
                val jarEntry = jarInputStream.nextJarEntry ?: break
                Assertions.assertNull(jarEntry.codeSigners)
                jarInputStream.copyTo(OutputStream.nullOutputStream())
            }
        }

        val unsignedJarEntries = ZipInputStream(Files.newInputStream(cpk.mainJar)).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }.map {
                it.name to let {
                    val md = MessageDigest.getInstance(algo)
                    DigestInputStream(zipInputStream, md).copyTo(OutputStream.nullOutputStream())
                    SecureHash(algo, md.digest())
                }
            }.filter {
                !it.first.toUpperCase().endsWith(".SF")
            }.associate { it }
        }

        Assertions.assertEquals(originalEntries, unsignedJarEntries)
    }
}