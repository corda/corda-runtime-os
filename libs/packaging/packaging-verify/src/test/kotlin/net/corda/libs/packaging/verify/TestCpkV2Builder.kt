package net.corda.libs.packaging.verify

import net.corda.libs.packaging.verify.TestUtils.addFile
import net.corda.libs.packaging.verify.TestUtils.signedBy
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

internal class TestCpkV2Builder {
    var name = "testCpkV2.cpk"
        private set
    var version = "1.0.0.0"
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
    fun version(version: String) = apply { this.version = version }
    fun manifest(manifest: Manifest) = apply { this.manifest = manifest }
    fun libraries(vararg libraries: TestUtils.Library) = apply { this.libraries = arrayOf(*libraries) }
    fun dependencies(vararg dependencies: TestUtils.Dependency) = apply { this.dependencies = arrayOf(*dependencies) }
    fun signers(vararg signers: TestUtils.Signer) = apply { this.signers = arrayOf(*signers) }
    fun build() =
        InMemoryZipFile().apply {
            setManifest(manifest ?: cpkV2Manifest())
            addFile("META-INF/CPKDependencies", cpkV2Dependencies(dependencies))
            libraries.forEach { addFile("META-INF/privatelib/${it.name}", it.content) }
            for (i in 1..3) addFile("package/Cpk$i.class")
            addFile("migration/schema-v1.changelog-master.xml")
            addFile("migration/schema.changelog-init.xml")
        }.signedBy(signers = signers)

    private fun cpkV2Manifest() =
        Manifest().apply {
            read(
                ByteArrayInputStream("""
                Manifest-Version: 1.0
                Corda-CPK-Format: 2.0
                Corda-CPK-Cordapp-Name: $name
                Corda-CPK-Cordapp-Version: $version
                Corda-CPK-Cordapp-Vendor: R3
                """.trimIndent().plus("\n").toByteArray())
            )
        }

    private fun cpkV2Dependencies(dependencies: Array<TestUtils.Dependency>) =
        buildString {
            append("[")
            dependencies.forEachIndexed { i, it ->
                if (i > 0) append(",")
                append("""
                {
                    "name": "${it.name}",
                    "version": "${it.version}",
                    "verifySameSignerAsMe": true
                }
                """.trimIndent().plus("\n"))
            }
            append("]")
        }
}
