package net.corda.cli.plugins.packaging

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import net.corda.cli.plugins.packaging.TestUtils.captureStdErr
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine

class CreateCpbTest {

    @TempDir
    lateinit var tempDir: Path

    private val app = CreateCpb()

    internal companion object {
        const val CREATED_CPB_NAME = "cpb-test-outcome.cpb"

        private val testKeyStore = Path.of(this::class.java.getResource("/signingkeys.pfx")?.toURI()
            ?: error("signingkeys.pfx not found"))
    }

    private fun buildTestCpk(
        jars: List<String>,
        cpkName: Path = Path.of(tempDir.toString(), "${UUID.randomUUID()}.cpk")
    ): Path {
        JarOutputStream(Files.newOutputStream(cpkName, StandardOpenOption.CREATE_NEW)).use { jarOs ->
            jars.forEach {
                jarOs.putNextEntry(JarEntry(it))
                jarOs.write("TEST CONTENT".toByteArray())
            }
            jarOs.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            jarOs.write("TEST CONTENT".toByteArray())
        }
        return cpkName
    }

    @Test
    fun `packs CPKs into CPB`() {
        val cpk0 = buildTestCpk(
            listOf(
                "lib/cpk0-lib.jar",
                "main-bundle0.jar"
            )
        )
        val cpk1 = buildTestCpk(
            listOf(
                "lib/cpk1-lib.jar",
                "main-bundle1.jar"
            )
        )

        val outcomeCpb = Path.of("$tempDir/$CREATED_CPB_NAME")

        CommandLine(app)
            .execute(
                "$cpk0",
                "$cpk1",
                "--cpb-name=testCpb",
                "--cpb-version=5.0.0.0-SNAPSHOT",
                "--file=$outcomeCpb",
                "--keystore=$testKeyStore",
                "--storepass=keystore password",
                "--key=signing key 1"
            )

        checkCpbContainsEntries(
            outcomeCpb,
            listOf(
                cpk0.toString(),
                cpk1.toString()
            ).map { Path.of(it).fileName.toString() }
        )
    }

    @Test
    fun `throws if CPK is missing`() {
        val cpk0 = buildTestCpk(
            listOf(
                "lib/cpk0-lib.jar",
                "main-bundle0.jar"
            )
        )
        val missingCpk = Path.of("missing.cpk")

        val errorText = captureStdErr {
            CommandLine(app)
                .execute(
                    "$cpk0",
                    "$missingCpk",
                    "--cpb-name=testCpb",
                    "--cpb-version=5.0.0.0-SNAPSHOT",
                    "--file=never-generated-cpb.cpb",
                    "--keystore=$testKeyStore",
                    "--storepass=keystore password",
                    "--key=signing key 1"
                )
        }

        assertTrue(errorText.contains("java.lang.IllegalArgumentException: \"missing.cpk\" does not exist or is not readable"))
    }

    @Test
    fun `signature is added`() {
        val cpk0 = buildTestCpk(
            listOf(
                "lib/cpk0-lib.jar",
                "main-bundle0.jar"
            )
        )
        val cpk1 =  buildTestCpk(
            listOf(
                "lib/cpk1-lib.jar",
                "main-bundle1.jar"
            )
        )

        val outcomeCpb = Path.of("$tempDir/$CREATED_CPB_NAME")

        CommandLine(app)
            .execute(
                "$cpk0",
                "$cpk1",
                "--cpb-name=testCpb",
                "--cpb-version=5.0.0.0-SNAPSHOT",
                "--file=$outcomeCpb",
                "--keystore=$testKeyStore",
                "--storepass=keystore password",
                "--key=signing key 1"
            )

        checkCpbContainsEntries(
            outcomeCpb,
            listOf("META-INF/CPB-SIG.SF", "META-INF/CPB-SIG.RSA")
        )
    }
}

private fun checkCpbContainsEntries(cpb: Path, expectedEntries: List<String>) {
    JarInputStream(Files.newInputStream(cpb)).use {
        assertTrue(it.manifest.mainAttributes.isNotEmpty())
        assertTrue(it.manifest.mainAttributes[Attributes.Name("Corda-CPB-Format")] == "2.0")
        assertTrue(it.manifest.mainAttributes[Attributes.Name("Corda-CPB-Name")] == "testCpb")
        assertTrue(it.manifest.mainAttributes[Attributes.Name("Corda-CPB-Version")] == "5.0.0.0-SNAPSHOT")
        assertTrue(it.manifest.mainAttributes[Attributes.Name("Corda-CPB-Upgrade")] == false.toString())

        val jarEntries = mutableListOf<ZipEntry>()
        var jarEntry: JarEntry? = it.nextJarEntry
        while (jarEntry != null) {
            jarEntries.add(jarEntry)
            jarEntry = it.nextJarEntry
        }
        assertTrue {
            val actualEntries = jarEntries.map { it.name }
            expectedEntries.all {
                actualEntries.contains(it)
            }
        }
    }
}