package net.corda.libs.packaging.testutils.cpb

import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.addFile
import net.corda.libs.packaging.testutils.TestUtils.signedBy
import net.corda.libs.packaging.testutils.cpk.TestCpkV1Builder
import net.corda.test.util.InMemoryZipFile
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

class TestCpbV1Builder {
    var name = "testCpbV1.cpb"
        private set
    var version = "1.0.0.0"
        private set
    var manifest: Manifest? = null
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
    fun cpks(vararg cpks: TestCpkV1Builder) = apply { this.cpks = arrayOf(*cpks) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpbV1Manifest())
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
