package net.corda.libs.packaging.verify

import net.corda.libs.packaging.verify.TestUtils.addFile
import net.corda.libs.packaging.verify.TestUtils.signedBy
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

internal class TestCpbV1Builder {
    companion object {
        val POLICY_FILE = "META-INF/GroupPolicy.json"
    }
    var name = "testCpbV1"
        private set
    var version = "1.0.0.0"
        private set
    var manifest: Manifest? = null
        private set
    var policy = "{\"groupId\":\"test\"}"
        private set
    var cpks = arrayOf<TestCpkV1Builder>(
            TestCpkV1Builder().name("testCpk1.cpk").version("1.0.0.0"),
            TestCpkV1Builder().name("testCpk2.cpk").version("2.0.0.0")
                .dependencies(TestUtils.Dependency("testCpk1.cpk", "1.0.0.0"))
        )
        private set
    var signers = emptyArray<TestUtils.Signer>()
        private set

    fun name(name: String) = apply { this.name = name }
    fun version(version: String) = apply { this.version = version }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun policy(policy: String) = apply { this.policy = policy }
    fun cpks(vararg cpks: TestCpkV1Builder) = apply { this.cpks = arrayOf(*cpks) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpbV1Manifest())
            addFile(POLICY_FILE, policy)
            cpks.forEach {
                val fileName = "${it.name.removeSuffix(".cpk")}-$version.cpk"
                val cpk = it.signers(signers = signers).build()
                addFile(fileName, cpk.toByteArray())
                cpk.close()
            }
        }.signedBy(signers = signers)

    private fun cpbV1Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPI-Format: 1.0
                Corda-CPB-Name: $name
                Corda-CPB-Version: $version
                """.trimIndent().plus("\n").toByteArray())
            )
        }
}
