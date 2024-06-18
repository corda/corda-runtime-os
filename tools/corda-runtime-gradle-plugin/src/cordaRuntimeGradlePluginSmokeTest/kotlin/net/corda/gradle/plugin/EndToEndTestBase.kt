package net.corda.gradle.plugin

import kotlinx.coroutines.runBlocking
import net.corda.restclient.CordaRestClient
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.Writer
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

// https://docs.gradle.org/current/userguide/test_kit.html
abstract class EndToEndTestBase {
    companion object {
        private val targetUrl = URI("https://localhost:8888")
        private const val USER = "admin"
        private const val PASSWORD = "admin"

        @JvmStatic
        protected val restClient = CordaRestClient.createHttpClient(targetUrl, USER, PASSWORD, insecure = true)

        private val composeFile = File(this::class.java.getResource("/config/combined-worker-compose.yml")!!.toURI())
        private const val CORDA_RUNTIME_VERSION_STABLE = "5.3.0.0-HC01"
        private val testEnvCordaImageTag = System.getenv("CORDA_IMAGE_TAG") ?: CORDA_RUNTIME_VERSION_STABLE

        @JvmStatic
        protected fun waitUntilRestOrThrow(timeout: Duration = Duration.ofSeconds(120), throwError: Boolean = true) {
            val start = Instant.now()
            while (true) {
                try {
                    restClient.helloRestClient.getHelloGetprotocolversion()
                    return
                } catch (e: Exception) {
                    if (Duration.between(start, Instant.now()) > timeout) {
                        if (!throwError) return
                        else throw IllegalStateException("Failed to connect to Corda REST API", e)
                    }
                    Thread.sleep(10 * 1000)
                }
            }
        }

        private fun runComposeProjectCommand(vararg args: String): Process {
            val composeCommandBase = listOf(
                "docker",
                "compose",
                "-f",
                composeFile.absolutePath,
                "-p",
                "corda-cluster"
            )
            val cmd = composeCommandBase + args.toList()
            val cordaProcessBuilder = ProcessBuilder(cmd)
            cordaProcessBuilder.environment()["CORDA_RUNTIME_VERSION"] = testEnvCordaImageTag
            cordaProcessBuilder.redirectErrorStream(true)
            val process = cordaProcessBuilder.start()
            process.inputStream.transferTo(System.out)

            return process
        }

        @JvmStatic
        fun startCompose(wait: Boolean = true) {
            val cordaProcess = runComposeProjectCommand("up", "--pull", "missing", "--quiet-pull", "--detach")

            val hasExited = cordaProcess.waitFor(10, TimeUnit.SECONDS)
            if (cordaProcess.exitValue() != 0 || !hasExited) throw IllegalStateException("Failed to start Corda cluster using docker compose")

            if (wait) runBlocking { waitUntilRestOrThrow() }

            runComposeProjectCommand("images").waitFor(10, TimeUnit.SECONDS)
        }

        @JvmStatic
        fun stopCompose() {
            val cordaStopProcess = runComposeProjectCommand("down")
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
        val sourceConfigFolder = File("src/cordaRuntimeGradlePluginSmokeTest/resources/")
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
                cordaRestUser = "$USER"
                cordaRestPasswd ="$PASSWORD"
                notaryVersion = "$testEnvCordaImageTag"
                runtimeVersion = "$CORDA_RUNTIME_VERSION_STABLE"
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

    fun executeWithRunner(vararg args: String, outputWriter: Writer? = null, forwardOutput: Boolean = false): BuildResult {
        val gradleRunnerBuilder = GradleRunner
            .create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(*args)
        if (outputWriter != null) {
            gradleRunnerBuilder
                .forwardStdOutput(outputWriter)
                .forwardStdError(outputWriter)
        }
        if (forwardOutput) gradleRunnerBuilder.forwardOutput()
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
