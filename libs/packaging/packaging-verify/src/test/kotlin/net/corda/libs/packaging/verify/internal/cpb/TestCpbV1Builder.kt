package net.corda.libs.packaging.verify.internal.cpb

import net.corda.libs.packaging.verify.InMemoryZipFile
import net.corda.libs.packaging.verify.TestUtils
import net.corda.libs.packaging.verify.TestUtils.addFile
import net.corda.libs.packaging.verify.TestUtils.signedBy
import net.corda.libs.packaging.verify.internal.cpk.TestCpkV1Builder
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

internal class TestCpbV1Builder {
    companion object {
        val POLICY_FILE = "META-INF/GroupPolicy.json"
    }
    var name = "testCpbV1.cpb"
        private set
    var version = "1.0.0.0"
        private set
    var manifest: Manifest? = null
        private set
    var policy = "{\"groupId\":\"test\"}"
        private set
    var cpks = arrayOf<TestCpkV1Builder>(
            TestCpkV1Builder().name("testCpk1-1.0.0.0.cpk").bundleName("test.cpk1").bundleVersion("1.0.0.0"),
            TestCpkV1Builder().name("testCpk2-2.0.0.0.cpk").bundleName("test.cpk2").bundleVersion("2.0.0.0")
                .dependencies(TestUtils.Dependency("test.cpk1", "1.0.0.0"))
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
                if (it.signers.isEmpty()) it.signers(signers = signers)
                it.build().use { cpk ->
                    addFile(it.name, cpk.toByteArray())
                }
            }
        }.signedBy(signers = signers)

    private fun cpbV1Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPB-Format: 1.0
                Corda-CPB-Name: $name
                Corda-CPB-Version: $version
                """.trimIndent().plus("\n").toByteArray())
            )
        }
}
