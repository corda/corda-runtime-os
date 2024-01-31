package net.corda.gradle.plugin

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

    protected val restHostnameWithPort = "localhost:8888"

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
                cordaClusterURL = "https://$restHostnameWithPort"
                cordaRestUser = "admin"
                cordaRpcPasswd ="admin"
                combinedWorkerVersion = "5.0.1.0"
                postgresJdbcVersion = "42.4.3"
                cordaDbContainerName = "corda-runtime-gradle-plugin-postgresql"
                networkConfigFile = "config/static-network-config.json"
                r3RootCertFile = "config/r3-ca-key.pem"
                corDappCpiName = "MyCorDapp"
                notaryCpiName = "NotaryServer"
                workflowsModuleName = "workflowsModule"
                cordaRuntimePluginWorkspaceDir = "workspace"
                cordaProcessorTimeout = "-1"
                vnodeRegistrationTimeout = "30000"
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
