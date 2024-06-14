package net.corda.gradle.plugin.queries

import kotlinx.coroutines.runBlocking
import net.corda.gradle.plugin.EndToEndTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit

class QueriesSmokeTest : EndToEndTestBase() {
    companion object {
        private val composeFile = File(this::class.java.getResource("/config/combined-worker-compose.yml")!!.toURI())

        @JvmStatic
        @BeforeAll
        fun startCombinedWorker() {
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
            cordaProcessBuilder.environment()["CORDA_RUNTIME_VERSION"] = "5.3.0.0-HC01"
            cordaProcessBuilder.redirectErrorStream(true)
            val cordaProcess = cordaProcessBuilder.start()
            cordaProcess.inputStream.transferTo(System.out)
            cordaProcess.waitFor(10, TimeUnit.SECONDS)
            if (cordaProcess.exitValue() != 0) throw IllegalStateException("Failed to start Corda cluster using docker compose")
            runBlocking { waitUntilRestApiIsAvailable() }
        }

        @JvmStatic
        @AfterAll
        fun stopCombinedWorker() {
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

    @Test
    fun listVNodesIsSuccessful() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_VNODES_TASK_NAME, "--info")
        assertThat(result.output)
            .containsPattern("CPI Name\\s+Holding identity short hash\\s+X500 Name")
    }

    @Test
    fun listCPIsIsSuccessful() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_CPIS_TASK_NAME)
        assertThat(result.output)
            .containsPattern("CpiName\\s+CpiVersion\\s+CpiFileCheckSum")
    }
}
