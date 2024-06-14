package net.corda.gradle.plugin

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.restclient.CordaRestClient
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

// https://docs.gradle.org/current/userguide/test_kit.html
abstract class EndToEndTestBase {
    companion object {
        private val targetUrl = DEFAULT_CLUSTER.rest.uri
        private val user = DEFAULT_CLUSTER.rest.user
        private val password = DEFAULT_CLUSTER.rest.password

        protected val restClient = CordaRestClient.createHttpClient(targetUrl, user, password)
        private val composeFile = File(this::class.java.getResource("/config/combined-worker-compose.yml")!!.toURI())
        private const val TEST_ENV_CORDA_RUNTIME_VERSION = "5.3.0.0-HC01"

        @JvmStatic
        protected suspend fun waitUntilRestApiIsAvailable() {
            val start = Instant.now()
            while (Duration.between(start, Instant.now()) < Duration.ofMinutes(2)) {
                try {
                    restClient.helloRestClient.getHelloGetprotocolversion()
                    break
                }
                catch (_: Exception) {
                    delay(10 * 1000)
                }
            }
        }

        @JvmStatic
        fun startCompose(wait: Boolean = true) {
            val cordaProcessBuilder = ProcessBuilder(
                "docker",
                "compose",
                "-f",
                composeFile.absolutePath,
                "-p",
                "corda-cluster",
                "up",
                "--pull",
                "missing",
                "--quiet-pull",
                "--detach"
            )
            cordaProcessBuilder.environment()["CORDA_RUNTIME_VERSION"] = TEST_ENV_CORDA_RUNTIME_VERSION
            cordaProcessBuilder.redirectErrorStream(true)
            val cordaProcess = cordaProcessBuilder.start()
            cordaProcess.inputStream.transferTo(System.out)
            cordaProcess.waitFor(10, TimeUnit.SECONDS)
            if (cordaProcess.exitValue() != 0) throw IllegalStateException("Failed to start Corda cluster using docker compose")
            if (wait) runBlocking { waitUntilRestApiIsAvailable() }
        }

        @JvmStatic
        fun stopCompose() {
            val cordaProcessBuilder = ProcessBuilder(
                "docker",
                "compose",
                "-f",
                composeFile.absolutePath,
                "-p",
                "corda-cluster",
                "down",
            )
            cordaProcessBuilder.redirectErrorStream(true)
            val cordaStopProcess = cordaProcessBuilder.start()
            cordaStopProcess.inputStream.transferTo(System.out)
            cordaStopProcess.waitFor(30, TimeUnit.SECONDS)
        }
    }

    @field:TempDir
    lateinit var projectDir: File
    protected lateinit var buildFile: File
    private lateinit var networkPath: String

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
        val sourceConfigFolder = File("src/endToEndTest/resources/")
        val targetConfigFolder = File("$projectDir/")
        sourceConfigFolder.absoluteFile.copyRecursively(targetConfigFolder)
    }

    fun appendCordaRuntimeGradlePluginExtension(
        appendArtifactoryCredentials: Boolean = false,
        isStaticNetwork: Boolean = true
    ) {

        networkPath = if (isStaticNetwork) {
            "config/static-network-config.json"
        } else {
            "config/dynamic-network-config.json"
        }

        buildFile.appendText(
            """
            cordaRuntimeGradlePlugin {
                cordaClusterURL = "$targetUrl"
                cordaRestUser = "$user"
                cordaRestPasswd ="$password"
                notaryVersion = "5.3.0.0-HC01"
                runtimeVersion = "5.3.0.0-HC01"
                composeFilePath = "config/combined-worker-compose.yml"
                networkConfigFile = "$networkPath"
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
        if (appendArtifactoryCredentials)
            appendArtifactoryCredentialsToTheCordaRuntimeGradlePluginExtension()
    }

    private fun appendArtifactoryCredentialsToTheCordaRuntimeGradlePluginExtension() {
        val existingContent = buildFile.readText()
        val newContent = if (existingContent.contains("cordaRuntimeGradlePlugin")) {
            existingContent.replace(
                "cordaRuntimeGradlePlugin {",
                """
                     cordaRuntimeGradlePlugin {
                        artifactoryUsername = findProperty('cordaArtifactoryUsername') ?: System.getenv('CORDA_ARTIFACTORY_USERNAME')
                        artifactoryPassword = findProperty('cordaArtifactoryPassword') ?: System.getenv('CORDA_ARTIFACTORY_PASSWORD')
                """.trimIndent()
            )
        } else {
            existingContent + """
                cordaRuntimeGradlePlugin {
                    artifactoryUsername = findProperty('cordaArtifactoryUsername') ?: System.getenv('CORDA_ARTIFACTORY_USERNAME')
                    artifactoryPassword = findProperty('cordaArtifactoryPassword') ?: System.getenv('CORDA_ARTIFACTORY_PASSWORD')
                }
            """.trimIndent()
        }
        buildFile.writeText(newContent)
    }

    fun executeWithRunner(vararg args: String, forwardOutput: Boolean = false): BuildResult {
        val gradleRunnerBuilder = GradleRunner
            .create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(*args)
        if (forwardOutput) {
            gradleRunnerBuilder.forwardOutput()
        }
        return gradleRunnerBuilder.build()
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
