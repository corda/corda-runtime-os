package net.corda.gradle.plugin.queries


import net.corda.gradle.plugin.EndToEndTestBase
import net.corda.gradle.plugin.cordalifecycle.CordaLifecycleHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class QueriesSmokeTest : EndToEndTestBase() {
    companion object {
        private val pidCacheFile: File = File.createTempFile("combined-worker-compose", ".pid").also {
            it.delete()
        }

        @JvmStatic
        @BeforeAll
        fun startCombinedWorker() {
            val composeFile = File(this::class.java.getResource("/config/combined-worker-compose.yml")!!.toURI())

            CordaLifecycleHelper().startCombinedWorkerWithDockerCompose(
                pidCacheFile.absolutePath,
                composeFile.absolutePath,
                "corda-cluster",
                "5.3.0.0-HC01"
            )
            // TODO: do this in a thread and wait for the REST API to be available
        }

        @JvmStatic
        @AfterAll
        fun stopCombinedWorker() {
            CordaLifecycleHelper().stopCombinedWorkerProcess(pidCacheFile.absolutePath)
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
