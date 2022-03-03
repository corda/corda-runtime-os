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
class CpiWithGroupPolicyTests {

    private lateinit var testDir: Path

    private lateinit var flowsCPKPath: Path
    private lateinit var workflowCPKPath: Path
    private lateinit var contractCPKPath: Path
    private lateinit var testCPIPath: Path
    private lateinit var contractCPK: CPK.Metadata
    private lateinit var workflowCPK: CPK.Metadata
    private lateinit var flowsCPK: CPK.Metadata
    private lateinit var testCPI: CPI
    private val cpiName = "Test CPI"
    private val cpiVersion = "1.5"
    private lateinit var cpiHash: SecureHash

    private val groupId = "53ab914b-e746-4e85-8d4f-38b38fa3152b"
    private val groupPolicy = "{ groupId: '$groupId'}"

    @BeforeAll
    fun setup(@TempDir junitTestDir: Path) {
        testDir = junitTestDir

        flowsCPKPath = Path.of(URI(System.getProperty("net.corda.flows.cpk")))
        flowsCPK = Files.newInputStream(flowsCPKPath).use { CPK.Metadata.from(it, flowsCPKPath.toString()) }
        testCPIPath = testDir.resolve("test.cpi")
        workflowCPKPath = Path.of(URI(System.getProperty("net.corda.packaging.test.workflow.cpk")))
        contractCPKPath = Path.of(URI(System.getProperty("net.corda.packaging.test.contract.cpk")))
        contractCPK = Files.newInputStream(contractCPKPath).use { CPK.Metadata.from(it, contractCPKPath.toString()) }
        workflowCPK = Files.newInputStream(workflowCPKPath).use { CPK.Metadata.from(it, workflowCPKPath.toString()) }
        Files.newOutputStream(testCPIPath).use {
            CPI.assemble(it, cpiName, cpiVersion, listOf(workflowCPKPath, contractCPKPath), emptyList(), groupPolicy = groupPolicy)
        }
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        testCPI = DigestInputStream(Files.newInputStream(testCPIPath), md).use { inputStream ->
            CPI.from(inputStream, testDir.resolve("cpi_expansion_dir"))
        }
        cpiHash = SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest())
    }

    @AfterAll
    fun teardown() {
        testCPI.close()
    }

    @Test
    fun `check all CPKs have been included and their metadata are correct and group policy has been read`() {
        Assertions.assertEquals(cpiName, testCPI.metadata.id.name)
        Assertions.assertEquals(cpiVersion, testCPI.metadata.id.version)
        Assertions.assertEquals(cpiHash, testCPI.metadata.hash)

        val expectedCPKs = sequenceOf(workflowCPK, contractCPK).toCollection(TreeSet(Comparator.comparing(CPK.Metadata::id)))
        val actualCPKs = testCPI.cpks.asSequence().map { it.metadata }.toCollection(TreeSet(Comparator.comparing(CPK.Metadata::id)))
        Assertions.assertEquals(expectedCPKs, actualCPKs)

        Assertions.assertEquals(testCPI.metadata.groupPolicy, groupPolicy)
    }
}
