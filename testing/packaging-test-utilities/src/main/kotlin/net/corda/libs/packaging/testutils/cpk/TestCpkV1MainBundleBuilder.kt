package net.corda.libs.packaging.testutils.cpk

import net.corda.libs.packaging.testutils.TestUtils
import net.corda.libs.packaging.testutils.TestUtils.addFile
import net.corda.libs.packaging.testutils.TestUtils.signedBy
import net.corda.libs.packaging.testutils.TestUtils.toBase64
import net.corda.test.util.InMemoryZipFile
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

class TestCpkV1MainBundleBuilder {
    var name = "testMainBundle.jar"
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
            setManifest(manifest ?: cpkV1MainBundleManifest())
            addFile("META-INF/CPKDependencies", cpkV1Dependencies(dependencies))
            addFile("META-INF/DependencyConstraints", cpkV1DependencyConstraints(libraries))
            for (i in 1..3) addFile("package/Cpk$i.class")
            addFile("migration/schema-v1.changelog-master.xml")
            addFile("migration/schema.changelog-init.xml")
        }.signedBy(signers = signers)

    private fun cpkV1MainBundleManifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                $CPK_BUNDLE_NAME_ATTRIBUTE: $bundleName
                $CPK_BUNDLE_VERSION_ATTRIBUTE: $bundleVersion
                Cordapp-Workflow-Licence: Test-Licence
                Cordapp-Workflow-Name: Cpk used for unit tests
                Cordapp-Workflow-Vendor: R3
                Cordapp-Workflow-Version: 1
                """.trimIndent().plus("\n").toByteArray())
            )
        }

    private fun cpkV1Dependencies(dependencies: Array<TestUtils.Dependency>) =
        buildString {
            append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cpkDependencies xmlns="urn:corda-cpk">
                """.trimIndent())
            dependencies.forEach {
                if (it.hash != null) append(signerDependency(it))
                else append(sameAsMeDependency(it))
            }
            append("</cpkDependencies>")
        }

    private fun sameAsMeDependency(dependency: TestUtils.Dependency) =
        """
        <cpkDependency>
            <name>${dependency.name}</name>
            <version>${dependency.version}</version>
            <signers>
                <sameAsMe/>
            </signers>
        </cpkDependency>
        """.trimIndent().plus("\n")

    private fun signerDependency(dependency: TestUtils.Dependency) =
        """
        <cpkDependency>
            <name>net.acme.contract</name>
            <version>1.0.0</version>
            <signers>
                <signer algorithm="${dependency.hash!!.algorithm}">${dependency.hash.toBase64()}</signer>
            </signers>
        </cpkDependency>
        """.trimIndent().plus("\n")

    private fun cpkV1DependencyConstraints(libraries: Array<TestUtils.Library>) =
        buildString {
            append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <dependencyConstraints xmlns="urn:corda-cpk">
                """.trimIndent())
            libraries.forEach {
                append("""
                <dependencyConstraint>
                    <fileName>${it.name}</fileName>
                    <hash algorithm="SHA-256">${it.hash}</hash>
                </dependencyConstraint>
                """.trimIndent().plus("\n"))
            }
            append("</dependencyConstraints>")
        }
}
