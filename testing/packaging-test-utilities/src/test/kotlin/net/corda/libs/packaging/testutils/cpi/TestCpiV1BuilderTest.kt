package net.corda.libs.packaging.testutils.cpi

import java.util.jar.JarInputStream
import java.util.jar.Manifest
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.libs.packaging.testutils.TestUtils.BOB
import net.corda.libs.packaging.testutils.cpb.TestCpbV1Builder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestCpiV1BuilderTest {

    companion object {
        const val CPB_SIGNER_NAME = "CPB_SIG"
        const val CPI_SIGNER_NAME = "CPI_SIG"
        val CPB_SIGNER = TestUtils.Signer(CPB_SIGNER_NAME, ALICE.privateKeyEntry)
        val CPI_SIGNER = TestUtils.Signer(CPI_SIGNER_NAME, BOB.privateKeyEntry)
    }

    @Test
    fun `TestCpiV1Builder produces expected cpi v1 format`() {
        val cpiV1 =
            TestCpiV1Builder()
                .cpb(
                    TestCpbV1Builder()
                        .signers(CPB_SIGNER)
                )
                .signers(CPI_SIGNER)
                .build()
                .inputStream()

        JarInputStream(cpiV1).use { cpiInStream ->
            val manifest = cpiInStream.manifest
            assertCpiV1Manifest(manifest)
            assertCpiV1JarEntries(cpiInStream)
        }
    }
}

@Suppress("NestedBlockDepth", "ComplexMethod")
private fun assertCpiV1Manifest(cpiV1Manifest: Manifest) {
    // main attributes
    var containsCpbFormat = false
    var containsCpbName = false
    var containsCpbVersion = false

    // signing related entries
    var containsGroupPolicySig = false
    var containsTestCpk1Sig = false
    var containsTestCpk2Sig = false

    run breaking@{
        cpiV1Manifest.mainAttributes.forEach {
            when (it.key.toString()) {
                "Corda-CPB-Format" -> {
                    if (it.value != "1.0")
                        return@breaking
                    containsCpbFormat = true
                }
                "Corda-CPB-Name" -> {
                    if (it.value != "testCpbV1.cpb")
                        return@breaking
                    containsCpbName = true
                }
                "Corda-CPB-Version" -> {
                    if (it.value != "1.0.0.0")
                        return@breaking
                    containsCpbVersion = true
                }
            }
        }
    }

    cpiV1Manifest.entries.forEach {
        when (it.key) {
            "META-INF/GroupPolicy.json" -> containsGroupPolicySig = true
            "testCpk1-1.0.0.0.cpk" -> containsTestCpk1Sig = true
            "testCpk2-2.0.0.0.cpk" -> containsTestCpk2Sig = true
        }
    }

    assertTrue(containsCpbFormat)
    assertTrue(containsCpbName)
    assertTrue(containsCpbVersion)
    assertTrue(containsGroupPolicySig)
    assertTrue(containsTestCpk1Sig)
    assertTrue(containsTestCpk2Sig)
    assertTrue(containsCpbFormat)
}

@Suppress("ComplexMethod")
private fun assertCpiV1JarEntries(cpiInStream: JarInputStream) {
    var testCpk1Exists = false
    var testCpk2Exists = false
    var cpbSigFileExists = false
    var cpbSigBlockFileExists = false
    var cpiSigFileExists = false
    var cpiSigBlockFileExists = false
    var groupPolicyExists = false

    while (true) {
        val nextEntry = cpiInStream.nextEntry ?: break

        when (nextEntry.name) {
            "testCpk1-1.0.0.0.cpk" -> testCpk1Exists = true
            "testCpk2-2.0.0.0.cpk" -> testCpk2Exists = true
            "META-INF/${TestCpiV1BuilderTest.CPB_SIGNER_NAME}.SF" -> cpbSigFileExists = true
            "META-INF/${TestCpiV1BuilderTest.CPB_SIGNER_NAME}.EC" -> cpbSigBlockFileExists = true
            "META-INF/${TestCpiV1BuilderTest.CPI_SIGNER_NAME}.SF" -> cpiSigFileExists = true
            "META-INF/${TestCpiV1BuilderTest.CPI_SIGNER_NAME}.EC" -> cpiSigBlockFileExists = true
            "META-INF/GroupPolicy.json" -> groupPolicyExists = true
        }
    }

    assertTrue(testCpk1Exists)
    assertTrue(testCpk2Exists)
    assertTrue(cpbSigFileExists)
    assertTrue(cpbSigBlockFileExists)
    assertTrue(cpiSigFileExists)
    assertTrue(cpiSigBlockFileExists)
    assertTrue(groupPolicyExists)
}