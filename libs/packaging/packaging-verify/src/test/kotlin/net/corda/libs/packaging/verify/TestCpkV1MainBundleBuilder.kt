package net.corda.libs.packaging.verify

import net.corda.libs.packaging.verify.TestUtils.addFile
import net.corda.libs.packaging.verify.TestUtils.signedBy
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

internal class TestCpkV1MainBundleBuilder {
    var name = "testMainBundle.jar"
        private set
    var manifest = cpkV1MainBundleManifest()
        private set
    var libraries = IntRange(1, 3).map { TestUtils.Library("library$it.jar") }.toTypedArray()
        private set
    var dependencies = emptyArray<TestUtils.Dependency>()
        private set
    var signers = emptyArray<TestUtils.Signer>()
        private set

    fun name(name: String) = apply { this.name = name }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun libraries(vararg libraries: TestUtils.Library) = apply { this.libraries = arrayOf(*libraries) }
    fun dependencies(vararg dependencies: TestUtils.Dependency) = apply { this.dependencies = arrayOf(*dependencies) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest)
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
                append("""
                <cpkDependency>
                    <name>${it.name}</name>
                    <version>${it.version}</version>
                    <signers>
                        <sameAsMe/>
                    </signers>
                </cpkDependency>
                """.trimIndent().plus("\n"))
            }
            append("</cpkDependencies>")
        }

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