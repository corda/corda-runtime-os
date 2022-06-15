package net.corda.libs.packaging.verify

import net.corda.libs.packaging.verify.TestUtils.addFile
import net.corda.libs.packaging.verify.TestUtils.signedBy
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

internal class TestCpkV1Builder {
    var name = "testCpkV1.cpk"
        private set
    var version = "1.0.0.0"
        private set
    var manifest: Manifest? = null
        private set
    private val mainBundleBuilder = TestCpkV1MainBundleBuilder()
    var signers = emptyArray<TestUtils.Signer>()
        private set
    var mainBundleSigners = emptyArray<TestUtils.Signer>()
        private set

    fun name(name: String) = apply { this.name = name }
    fun version(version: String) = apply { this.version = version }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun libraries(vararg libraries: TestUtils.Library) = apply { this.mainBundleBuilder.libraries(*libraries) }
    fun dependencies(vararg dependencies: TestUtils.Dependency) = apply { this.mainBundleBuilder.dependencies(*dependencies) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun mainBundleSigners(vararg mainBundleSigners: TestUtils.Signer) = apply { this.mainBundleSigners = arrayOf(*mainBundleSigners) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpkV1Manifest())
            mainBundleBuilder.libraries.forEach { addFile("lib/${it.name}", it.content) }
            if (mainBundleSigners.isEmpty()) mainBundleSigners = signers
            val mainBundleName = "${this@TestCpkV1Builder.name.removeSuffix(".cpk")}-$version.jar"
            val mainBundle = mainBundleBuilder.signers(signers = mainBundleSigners).build()
            addFile(mainBundleName, mainBundle.toByteArray())
            mainBundle.close()
        }.signedBy(signers = signers)

    private fun cpkV1Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPK-Format: 1.0
                Corda-CPK-Cordapp-Name: $name
                Corda-CPK-Cordapp-Version: $version
                Corda-CPK-Cordapp-Licence: Test-Licence
                Corda-CPK-Cordapp-Vendor: R3
                """.trimIndent().plus("\n").toByteArray())
            )
        }
}
