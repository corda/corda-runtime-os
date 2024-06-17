package net.corda.gradle.plugin.cordalifecycle

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corda.gradle.plugin.EndToEndTestBase
import org.assertj.core.api.Assertions.catchThrowable
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.StringWriter

class CordaLifeCycleTasksTest : EndToEndTestBase() {

    @BeforeEach
    fun verifyRestIsUnavailable() {
        catchThrowable { restClient.helloRestClient.getHelloGetprotocolversion() }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun startAndStopCorda() {
        appendCordaRuntimeGradlePluginExtension()

        val startTaskWriter = StringWriter() // TODO: do we need this?

        lateinit var stopTaskResult: BuildResult
        runBlocking {
            GlobalScope.launch {
                executeWithRunner(START_CORDA_TASK_NAME, outputWriter = startTaskWriter)
            }
            val job = GlobalScope.launch {
                waitUntilRestApiIsAvailable(throwError = false)
                stopTaskResult = executeWithRunner(STOP_CORDA_TASK_NAME)
            }
            job.join()
            stopTaskResult.task(":$STOP_CORDA_TASK_NAME")!!.assertTaskSucceeded()
        }
    }
}
