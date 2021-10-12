package net.corda.packaging

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeSet

//This is to avoid extracting the CPK archive in every single test case,
// no test case writes anything to the filesystem, nor alters the state of the test class instance;
// this makes it safe to use the same instance for all test cases (test case execution order is irrelevant)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPITests {

    private lateinit var testDir : Path

    private lateinit var flowsCPKPath : Path
    private lateinit var workflowCPKPath : Path
    private lateinit var contractCPKPath : Path
    private lateinit var testCPIPath : Path
    private lateinit var contractCPK : CPK.Metadata
    private lateinit var workflowCPK : CPK.Metadata
    private lateinit var flowsCPK : CPK.Metadata
    private lateinit var testCPI: CPI
    private val cpiName = "Test CPI"
    private val cpiVersion = "1.5"


    @BeforeAll
    fun setup(@TempDir junitTestDir : Path) {
        testDir = junitTestDir

        flowsCPKPath = Path.of(URI(System.getProperty("net.corda.flows.cpk")))
        flowsCPK = CPK.Metadata.from(Files.newInputStream(flowsCPKPath), flowsCPKPath.toString())
        testCPIPath = testDir.resolve("test.cpi")
        workflowCPKPath = Path.of(URI(System.getProperty("net.corda.packaging.test.workflow.cpk")))
        contractCPKPath = Path.of(URI(System.getProperty("net.corda.packaging.test.contract.cpk")))
        contractCPK = CPK.Metadata.from(Files.newInputStream(contractCPKPath), contractCPKPath.toString())
        workflowCPK = CPK.Metadata.from(Files.newInputStream(workflowCPKPath), workflowCPKPath.toString())
        CPI.assemble(Files.newOutputStream(testCPIPath), cpiName, cpiVersion, listOf(workflowCPKPath, contractCPKPath))
        testCPI = CPI.from(Files.newInputStream(testCPIPath), testDir.resolve("cpi_expansion_dir"))
    }

    @Test
    fun `CPI metadata are correct`() {
        Assertions.assertEquals(cpiName, testCPI.metadata.id.name)
        Assertions.assertEquals(cpiVersion, testCPI.metadata.id.version)
        Assertions.assertNull(testCPI.metadata.id.identity)
    }

    @Test
    fun `check all CPKs have been included and their metadata are correct`() {
        val expectedCPKs = sequenceOf(workflowCPK, contractCPK).toCollection(TreeSet(Comparator.comparing(CPK.Metadata::id)))
        val actualCPKs = testCPI.cpks.asSequence().map { it.metadata }.toCollection(TreeSet(Comparator.comparing(CPK.Metadata::id)))
        Assertions.assertEquals(expectedCPKs, actualCPKs)
    }
}