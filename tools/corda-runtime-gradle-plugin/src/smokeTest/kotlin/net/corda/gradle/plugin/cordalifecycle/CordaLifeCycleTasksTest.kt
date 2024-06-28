package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.CombinedWorkerHelper.restClient
import net.corda.gradle.plugin.CombinedWorkerHelper.waitUntilRestOrThrow
import net.corda.gradle.plugin.SmokeTestBase
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.util.concurrent.Executors

class CordaLifeCycleTasksTest : SmokeTestBase() {

    private val executorService =  Executors.newSingleThreadExecutor()

    @BeforeEach
    fun verifyRestIsUnavailable() {
        catchThrowable { restClient.helloRestClient.getHelloGetprotocolversion() }
    }

    @AfterEach
    fun shutdownExecutorService() {
        executorService.shutdown()
    }

    @Test
    fun startAndStopCorda() {
        appendCordaRuntimeGradlePluginExtension()
        val startTaskOutput = StringWriter()

        val startTaskFuture = executorService.submit {
            val result = executeWithRunner(START_CORDA_TASK_NAME, outputWriter = startTaskOutput)
            result.task(":$START_CORDA_TASK_NAME")!!.assertTaskSucceeded()
        }
        waitUntilRestOrThrow(throwError = false)

        try {
            val result = executeWithRunner(STOP_CORDA_TASK_NAME)
            result.task(":$STOP_CORDA_TASK_NAME")!!.assertTaskSucceeded()
            // Start should exit successfully after stop
            startTaskFuture.get()
        } catch (e: Exception){
            // If stop task fails, check the start output first and throw if failed
            if (startTaskOutput.toString().lowercase().contains("failed")) {
                startTaskFuture.get()
            }
            throw e
        }
    }
}
