package net.corda.libs.packaging.testutils.cpk

import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.addFile
import net.corda.libs.packaging.testutils.TestUtils.signedBy
import net.corda.libs.packaging.testutils.TestUtils.toBase64
import net.corda.test.util.InMemoryZipFile
import java.io.ByteArrayInputStream
import java.util.jar.Manifest
import net.corda.libs.packaging.PackagingConstants
import net.corda.libs.packaging.internal.ExternalChannelsConfigLoader.Companion.EXTERNAL_CHANNELS_CONFIG_FILE_NAME

const val CPK_BUNDLE_NAME_ATTRIBUTE = "Bundle-SymbolicName"
const val CPK_BUNDLE_VERSION_ATTRIBUTE = "Bundle-Version"
const val CPK_WORKFLOW_LICENCE_ATTRIBUTE = "Cordapp-Workflow-Licence"
const val CPK_WORKFLOW_NAME_ATTRIBUTE = "Cordapp-Workflow-Name"
const val CPK_WORKFLOW_VENDOR_ATTRIBUTE = "Cordapp-Workflow-Vendor"
const val CPK_WORKFLOW_VERSION_ATTRIBUTE = "Cordapp-Workflow-Version"

class TestCpkV2Builder {
    var name = "testCpkV2-1.0.0.0.jar"
        private set
    var bundleName = "test.cpk"
        private set
    var bundleVersion = "1.0.0.0"
        private set
    var manifest: Manifest? = null
        private set
    var libraries = IntRange(1, 3).map { TestUtils.Library("library$it.jar") }.toTypedArray()
        private set
    var dependencies = emptyArray<TestUtils.Dependency>()
        private set
    var signers = emptyArray<TestUtils.Signer>()
        private set

    fun name(name: String) = apply { this.name = name }
    fun bundleName(bundleName: String) = apply { this.bundleName = bundleName }
    fun bundleVersion(bundleVersion: String) = apply { this.bundleVersion = bundleVersion }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun libraries(vararg libraries: TestUtils.Library) = apply { this.libraries = arrayOf(*libraries) }
    fun dependencies(vararg dependencies: TestUtils.Dependency) = apply { this.dependencies = arrayOf(*dependencies) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpkV2Manifest())
            addFile("META-INF/CPKDependencies.json", cpkV2Dependencies(dependencies))
            libraries.forEach { addFile("META-INF/privatelib/${it.name}", it.content) }
            for (i in 1..3) addFile("package/Cpk$i.class")
            addFile("migration/schema-v1.changelog-master.xml")
            addFile("migration/schema.changelog-init.xml")
            addFile(
                "${PackagingConstants.CPK_CONFIG_FOLDER}/$EXTERNAL_CHANNELS_CONFIG_FILE_NAME",
                content = TestUtils.EXTERNAL_CHANNELS_CONFIG_FILE_CONTENT
            )
        }.signedBy(signers = signers)

    private fun cpkV2Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPK-Format: 2.0
                $CPK_BUNDLE_NAME_ATTRIBUTE: $bundleName
                $CPK_BUNDLE_VERSION_ATTRIBUTE: $bundleVersion
                $CPK_WORKFLOW_LICENCE_ATTRIBUTE: Unknown
                $CPK_WORKFLOW_NAME_ATTRIBUTE: Test
                $CPK_WORKFLOW_VENDOR_ATTRIBUTE: R3
                $CPK_WORKFLOW_VERSION_ATTRIBUTE: 1
                """.trimIndent().plus("\n").toByteArray())
            )
        }

    private fun cpkV2Dependencies(dependencies: Array<TestUtils.Dependency>) =
        buildString {
            append("{\"formatVersion\":\"2.0\",\"dependencies\":[")
            dependencies.forEachIndexed { i, it ->
                if (i > 0) append(",")
                if (it.hash != null) append(hashDependency(it))
                else append(signerDependency(it))
            }
            append("]}")
        }

    private fun hashDependency(dependency: TestUtils.Dependency) =
        """
        {
            "name": "${dependency.name}",
            "version": "${dependency.version}",
            "verifyFileHash": {
                "algorithm": "${dependency.hash!!.algorithm}",
                "fileHash": "${dependency.hash.toBase64()}"
            }
        }
        """.trimIndent().plus("\n")

    private fun signerDependency(dependency: TestUtils.Dependency) =
        """
        {
            "name": "${dependency.name}",
            "version": "${dependency.version}",
            "verifySameSignerAsMe": true
        }
        """.trimIndent().plus("\n")

}
