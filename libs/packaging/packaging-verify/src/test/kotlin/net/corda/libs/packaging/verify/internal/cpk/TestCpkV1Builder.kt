package net.corda.libs.packaging.verify.internal.cpk

import net.corda.libs.packaging.verify.InMemoryZipFile
import net.corda.libs.packaging.verify.TestUtils
import net.corda.libs.packaging.verify.TestUtils.addFile
import net.corda.libs.packaging.verify.TestUtils.signedBy
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

internal class TestCpkV1Builder {
    var name = "testCpkV1-1.0.0.0.cpk"
        private set
    var manifest: Manifest? = null
        private set
    private val mainBundleBuilder = TestCpkV1MainBundleBuilder()
    var signers = emptyArray<TestUtils.Signer>()
        private set
    var mainBundleSigners = emptyArray<TestUtils.Signer>()
        private set

    fun name(name: String) = apply { this.name = name }
    fun bundleName(bundleName: String) = apply {  mainBundleBuilder.bundleName(bundleName) }
    fun bundleVersion(bundleVersion: String) = apply {  mainBundleBuilder.bundleVersion(bundleVersion) }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun libraries(vararg libraries: TestUtils.Library) = apply { mainBundleBuilder.libraries(*libraries) }
    fun dependencies(vararg dependencies: TestUtils.Dependency) = apply { mainBundleBuilder.dependencies(*dependencies) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun mainBundleSigners(vararg mainBundleSigners: TestUtils.Signer) = apply { this.mainBundleSigners = arrayOf(*mainBundleSigners) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpkV1Manifest())
            mainBundleBuilder.libraries.forEach { addFile("lib/${it.name}", it.content) }
            if (mainBundleSigners.isEmpty()) mainBundleSigners = signers
            val mainBundleName = "${this@TestCpkV1Builder.name.removeSuffix(".cpk")}.jar"
            mainBundleBuilder.signers(signers = mainBundleSigners).build().use { mainBundle->
                addFile(mainBundleName, mainBundle.toByteArray())
            }
        }.signedBy(signers = signers)

    private fun cpkV1Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPK-Format: 1.0
                """.trimIndent().plus("\n").toByteArray())
            )
        }
}
