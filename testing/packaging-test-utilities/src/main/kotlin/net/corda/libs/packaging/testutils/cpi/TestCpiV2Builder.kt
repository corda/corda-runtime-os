package net.corda.libs.packaging.testutils.cpi

import net.corda.test.util.InMemoryZipFile
import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.addFile
import net.corda.libs.packaging.testutils.TestUtils.signedBy
import net.corda.libs.packaging.testutils.cpb.TestCpbV2Builder
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

class TestCpiV2Builder {
    companion object {
        val POLICY_FILE = "META-INF/GroupPolicy.json"
    }
    var name = "testCpiV2.cpi"
        private set
    var version = "1.0.0.0"
        private set
    var manifest: Manifest? = null
        private set
    var policy = "{\"groupId\":\"test\"}"
        private set
    var cpb = TestCpbV2Builder()
        private set
    var signers = emptyArray<TestUtils.Signer>()
        private set

    fun name(name: String) = apply { this.name = name }
    fun version(version: String) = apply { this.version = version }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun policy(policy: String) = apply { this.policy = policy }
    fun cpb(cpb: TestCpbV2Builder) = apply { this.cpb = cpb }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpiV2Manifest())
            addFile(POLICY_FILE, policy)
            if (cpb.signers.isEmpty()) cpb.signers(signers = signers)
            cpb.build().use { cpb ->
                addFile(this@TestCpiV2Builder.cpb.name, cpb.toByteArray())
            }
        }.signedBy(signers = signers)

    private fun cpiV2Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPI-Format: 2.0
                Corda-CPI-Name: $name
                Corda-CPI-Version: $version
                """.trimIndent().plus("\n").toByteArray())
            )
        }
}
