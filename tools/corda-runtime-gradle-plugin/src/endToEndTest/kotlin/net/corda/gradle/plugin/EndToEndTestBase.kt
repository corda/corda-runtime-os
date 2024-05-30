package net.corda.gradle.plugin

import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

// https://docs.gradle.org/current/userguide/test_kit.html
abstract class EndToEndTestBase {
    // TODO based on integrationTest/kotlin/net/corda/gradle/plugin/FunctionalBaseTest.kt
    //  for now contains only necessary methods and properties

    @field:TempDir
    lateinit var projectDir: File
    protected lateinit var buildFile: File
    private lateinit var networkPath: String

    private val targetUrl = DEFAULT_CLUSTER.rest.uri
    private val user = DEFAULT_CLUSTER.rest.user
    private val password = DEFAULT_CLUSTER.rest.password

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
}
