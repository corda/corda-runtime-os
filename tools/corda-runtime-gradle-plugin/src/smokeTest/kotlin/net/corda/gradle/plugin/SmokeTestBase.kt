package net.corda.gradle.plugin

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

    @BeforeEach
    fun setup() {
        val sourceConfigFolder = File("src/smokeTest/resources/")
        val targetConfigFolder = File("$projectDir/")
        sourceConfigFolder.absoluteFile.copyRecursively(targetConfigFolder)

        val buildFile = targetConfigFolder.resolve("build.gradle")
        targetConfigFolder.resolve("test.build.gradle").renameTo(buildFile)
        require(buildFile.exists())

        val workflowsBuildFile = targetConfigFolder.resolve("workflows/build.gradle")
        targetConfigFolder.resolve("workflows/test.build.gradle").renameTo(workflowsBuildFile)
        require(workflowsBuildFile.exists())
    }

    fun executeWithRunner(
        vararg args: String,
        outputWriter: Writer? = null,
        forwardOutput: Boolean = false,
        isStaticNetwork: Boolean = true,
    ): BuildResult {
        return internalExecuteWithGradle(
            *args,
            outputWriter = outputWriter,
            forwardOutput = forwardOutput,
            isStaticNetwork = isStaticNetwork,
            buildAndFail = false,
        )
    }

    fun executeAndFailWithRunner(
        vararg args: String,
        outputWriter: Writer? = null,
        forwardOutput: Boolean = false,
        isStaticNetwork: Boolean = true,
    ): BuildResult {
        return internalExecuteWithGradle(
            *args,
            outputWriter = outputWriter,
            forwardOutput = forwardOutput,
            isStaticNetwork = isStaticNetwork,
            buildAndFail = true,
        )
    }

    @Suppress("SpreadOperator") // TODO remove this suppression
    private fun internalExecuteWithGradle(
        vararg args: String,
        outputWriter: Writer? = null,
        forwardOutput: Boolean = false,
        isStaticNetwork: Boolean,
        buildAndFail: Boolean,
    ): BuildResult {
        val networkPath = if (isStaticNetwork) {
            "config/static-network-config.json"
        } else {
            "config/dynamic-network-config.json"
        }
        val gradleProperties = GradleProperties(networkConfigFile = networkPath)
        val argsWithProperties = args.toList() + gradleProperties.toGradleCmdArgs()

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

        return if (buildAndFail) {
            gradleRunnerBuilder.buildAndFail()
        } else {
            gradleRunnerBuilder.build()
        }
    }

    fun BuildTask.assertTaskSucceeded() {
        assertTrue(outcome == TaskOutcome.SUCCESS, "The task '$path' is expected to be successful")
    }
}
