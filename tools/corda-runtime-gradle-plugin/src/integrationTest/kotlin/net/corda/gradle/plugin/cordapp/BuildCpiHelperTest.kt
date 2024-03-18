package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.FunctionalBaseTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.assertEquals

class BuildCpiHelperTest : FunctionalBaseTest() {
    private val resourcesDir = Path.of("src/integrationTest/resources")

    private val cpbFilePath = resourcesDir.resolve("test.cpb").absolutePathString()
    private val keyStoreFilePath = resourcesDir.resolve("signingkeys.pfx").absolutePathString()
    private val groupPolicyFilePath = resourcesDir.resolve("TestGroupPolicy.json").absolutePathString()

    private val outputCpiFile = Files.createTempFile("test-cpi", "cpi").also {
        it.deleteIfExists()
        it.toFile().deleteOnExit()
    }

    private val keystorePassword = "keystore password"
    private val signingKeyAlias = "signing key 1"
    private val cpiName = "test-cpi"

    @Test
    fun cpiIsCreated() {
        require(!outputCpiFile.exists()) { "Output CPI file should not exist" }

        assertDoesNotThrow {
            BuildCpiHelper().createCPI(
                groupPolicyFilePath,
                keyStoreFilePath,
                signingKeyAlias,
                keystorePassword,
                cpbFilePath,
                outputCpiFile.absolutePathString(),
                cpiName,
                "1.0"
            )
        }
        assertTrue(outputCpiFile.exists())

        JarInputStream(Files.newInputStream(outputCpiFile)).use { jar ->
            assertEquals(cpiName, jar.manifest.mainAttributes[Attributes.Name("Corda-CPI-Name")])
            val entries = jar.entryFileNames()
            assertTrue(entries.contains("GroupPolicy.json"))
            assertTrue(entries.contains("test.cpb"))
        }

    }

    private fun JarInputStream.entryFileNames(): List<String> =
        generateSequence { nextJarEntry }
            .map { it.name.substringAfter("/") }
            .toList()
}