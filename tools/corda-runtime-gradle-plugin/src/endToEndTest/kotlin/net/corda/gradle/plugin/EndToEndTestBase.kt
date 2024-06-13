package net.corda.gradle.plugin

import kotlinx.coroutines.delay
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

// https://docs.gradle.org/current/userguide/test_kit.html
abstract class EndToEndTestBase {
    @field:TempDir
    lateinit var projectDir: File
    protected lateinit var buildFile: File
    private lateinit var networkPath: String

    private val targetUrl = DEFAULT_CLUSTER.rest.uri
    private val user = DEFAULT_CLUSTER.rest.user
    private val password = DEFAULT_CLUSTER.rest.password

    protected val restClient = CordaRestClient.createHttpClient(targetUrl, user, password)

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

    fun executeWithRunner(vararg args: String, forwardOutput: Boolean = true): BuildResult {
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
