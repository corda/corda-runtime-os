package net.corda.packaging

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.TreeSet

// This is to avoid extracting the CPK archive in every single test case,
// no test case writes anything to the filesystem, nor alters the state of the test class instance;
// this makes it safe to use the same instance for all test cases (test case execution order is irrelevant)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CpiTests {

    private lateinit var testDir: Path

    private lateinit var flowsCpkPath: Path
    private lateinit var workflowCpkPath: Path
    private lateinit var contractCpkPath: Path
    private lateinit var testCpiPath: Path
    private lateinit var contractCpk: Cpk.Metadata
    private lateinit var workflowCpk: Cpk.Metadata
    private lateinit var flowsCpk: Cpk.Metadata
    private lateinit var testCpi: Cpi
    private val cpiName = "Test CPI"
    private val cpiVersion = "1.5"
    private lateinit var cpiHash: SecureHash

    @BeforeAll
    fun setup(@TempDir junitTestDir: Path) {
        testDir = junitTestDir

        testCpiPath = testDir.resolve("test.cpi")
        workflowCpkPath = Path.of(URI(System.getProperty("net.corda.packaging.test.workflow.cpk")))
        contractCpkPath = Path.of(URI(System.getProperty("net.corda.packaging.test.contract.cpk")))
        contractCpk = Files.newInputStream(contractCpkPath).use { Cpk.Metadata.from(it, contractCpkPath.toString()) }
        workflowCpk = Files.newInputStream(workflowCpkPath).use { Cpk.Metadata.from(it, workflowCpkPath.toString()) }
        Files.newOutputStream(testCpiPath).use {
            Cpi.assemble(it, cpiName, cpiVersion, listOf(workflowCpkPath, contractCpkPath))
        }
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        testCpi = DigestInputStream(Files.newInputStream(testCpiPath), md).use { inputStream ->
            Cpi.from(inputStream, testDir.resolve("cpi_expansion_dir"))
        }
        cpiHash = SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest())
    }

    @AfterAll
    fun teardown() {
        testCpi.close()
    }

    @Test
    fun `CPI metadata are correct`() {
        Assertions.assertEquals(cpiName, testCpi.metadata.id.name)
        Assertions.assertEquals(cpiVersion, testCpi.metadata.id.version)
        Assertions.assertEquals(cpiHash, testCpi.metadata.hash)
    }

    @Test
    fun `check all CPKs have been included and their metadata are correct`() {
        val expectedCpks = sequenceOf(workflowCpk, contractCpk).toCollection(TreeSet(Comparator.comparing(Cpk.Metadata::id)))
        val actualCpks = testCpi.cpks.asSequence().map { it.metadata }.toCollection(TreeSet(Comparator.comparing(Cpk.Metadata::id)))
        Assertions.assertEquals(expectedCpks, actualCpks)

        Assertions.assertEquals(testCpi.metadata.groupPolicy, null)
    }
}
