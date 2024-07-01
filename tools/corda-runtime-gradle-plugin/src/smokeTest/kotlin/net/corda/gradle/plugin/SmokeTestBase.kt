package net.corda.gradle.plugin

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.Writer

abstract class SmokeTestBase {

    @field:TempDir
    lateinit var projectDir: File
    private lateinit var buildFile: File
    private lateinit var networkPath: String

    private val artifactoryUsernameString = "findProperty('cordaArtifactoryUsername') ?: System.getenv('CORDA_ARTIFACTORY_USERNAME')"
    private val artifactoryPasswordString = "findProperty('cordaArtifactoryPassword') ?: System.getenv('CORDA_ARTIFACTORY_PASSWORD')"

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
        val sourceConfigFolder = File("src/smokeTest/resources/")
        val targetConfigFolder = File("$projectDir/")
        sourceConfigFolder.absoluteFile.copyRecursively(targetConfigFolder)

        val workflowsBuildFileDst = targetConfigFolder.resolve("workflows/build.gradle")
        targetConfigFolder.resolve("workflows/test.build.gradle").renameTo(workflowsBuildFileDst)
        assertThat(workflowsBuildFileDst.exists()).isTrue()
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
                notaryVersion = "$CORDA_RUNTIME_VERSION_STABLE"
                runtimeVersion = "$CORDA_RUNTIME_VERSION_STABLE"
                composeFilePath = "config/combined-worker-compose.yml"
                networkConfigFile = "$networkPath"
                r3RootCertFile = "config/Digicert-ca-key.pem"
                corDappCpiName = "MyCorDapp"
                notaryCpiName = "NotaryServer"
                workflowsModuleName = "workflows"
                cordaRuntimePluginWorkspaceDir = "workspace"
                cordaProcessorTimeout = "-1"
                vnodeRegistrationTimeout = "30000"
            }
            
            allprojects {
                version '1.0-SNAPSHOT'
                
                repositories {
                    mavenCentral()
                    mavenLocal()
                    
                    maven {
                        url = "${'$'}artifactoryContextUrl/corda-os-maven"
                        authentication {
                            basic(BasicAuthentication)
                        }
                        credentials {
                            username = $artifactoryUsernameString
                            password = $artifactoryPasswordString
                        }
                    }
                }
            }
            """.trimIndent()
        )
        if (appendArtifactoryCredentials)
            appendArtifactoryCredentialsToTheCordaRuntimeGradlePluginExtension()
    }

    private fun appendArtifactoryCredentialsToTheCordaRuntimeGradlePluginExtension() {
        val existingContent = buildFile.readText()
        val cordaRuntimeGradlePluginBlockCredentialsHeader = """
             cordaRuntimeGradlePlugin {
                artifactoryUsername = $artifactoryUsernameString
                artifactoryPassword = $artifactoryPasswordString
        """.trimIndent()
        val newContent = if (existingContent.contains("cordaRuntimeGradlePlugin")) {
            existingContent.replace(
                "cordaRuntimeGradlePlugin {",
                cordaRuntimeGradlePluginBlockCredentialsHeader
            )
        } else {
            listOf(existingContent, cordaRuntimeGradlePluginBlockCredentialsHeader, "}").joinToString("\n")
        }
        buildFile.writeText(newContent)
    }

    @Suppress("SpreadOperator") // TODO remove this suppression
    fun executeWithRunner(vararg args: String, outputWriter: Writer? = null, forwardOutput: Boolean = false): BuildResult {
        val argsWithProperties = args.toList() + GradleProperties().toKeyValues()
        val gradleRunnerBuilder = GradleRunner
            .create()
            .withPluginClasspath()
            .withProjectDir(projectDir)
            .withArguments(*argsWithProperties.toTypedArray())
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
