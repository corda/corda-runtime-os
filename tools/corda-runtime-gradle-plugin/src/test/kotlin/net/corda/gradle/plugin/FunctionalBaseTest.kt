package net.corda.gradle.plugin

//import net.corda.craft5.corda.cli.CordaCli
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

// https://docs.gradle.org/current/userguide/test_kit.html
abstract class FunctionalBaseTest {

    @field:TempDir
    lateinit var projectDir: File
    private lateinit var buildFile: File

    companion object {
//        @BeforeAll
//        @JvmStatic
//        fun beforeAll(cordaCli: CordaCli) {
//            cordaCli.execAndWait("") // Install the Corda CLI if needed
//        }
    }

    @BeforeEach
    fun setup() {
        buildFile = projectDir.resolve("build.gradle")
        buildFile.writeText(
            """
            plugins {
                id 'net.corda.gradle.plugin'
            }

            """.trimIndent()
        )
        val sourceConfigFolder = File("src/test/resources/")
        val targetConfigFolder = File("$projectDir/")
        sourceConfigFolder.absoluteFile.copyRecursively(targetConfigFolder)
    }

    fun appendCordaRuntimeGradlePluginExtension() {
        buildFile.appendText(
            """
            cordaRuntimeGradlePlugin {
                cordaClusterURL = "https://localhost:8888"
                cordaRpcUser = "admin"
                cordaRpcPasswd ="admin"
                combinedWorkerVersion = "5.0.0.0-Iguana1.0"
                postgresJdbcVersion = "42.4.3"
                cordaDbContainerName = "CSDEpostgresql"
            }
            repositories {
                mavenCentral()
                mavenLocal()
            }
            """.trimIndent()
        )
    }

    fun executeWithRunner(vararg args: String): BuildResult {
        return GradleRunner
            .create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(*args)
            .build()
    }

    fun executeAndFailWithRunner(vararg args: String): BuildResult {
        return GradleRunner
            .create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(*args)
            .buildAndFail()
    }

    fun BuildTask.assertTaskSucceeded() {
        assertTrue(outcome == TaskOutcome.SUCCESS, "The task '$path' is expected to be successful")
    }
}
