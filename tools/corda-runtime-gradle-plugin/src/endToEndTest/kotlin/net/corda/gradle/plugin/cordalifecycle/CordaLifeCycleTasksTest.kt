package net.corda.gradle.plugin.cordalifecycle

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corda.gradle.plugin.EndToEndTestBase
import net.corda.gradle.plugin.retry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CordaLifeCycleTasksTest : EndToEndTestBase() {

    @BeforeEach
    fun verifyRestIsUnavailable() {
        catchThrowable { restClient.helloRestClient.getHelloGetprotocolversion() }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun startAndStopCorda() {
        appendCordaRuntimeGradlePluginExtension()
        lateinit var result: BuildResult
        runBlocking {
            GlobalScope.launch {
                executeWithRunner(START_CORDA_TASK_NAME, forwardOutput = true)
                // TODO: do not clog console with output?
                // TODO: use Writer for output to assess output
            }
            val job = GlobalScope.launch {
                waitUntilRestApiIsAvailable(throwError = false)
                result = executeWithRunner(STOP_CORDA_TASK_NAME)
            }
            job.join()
            result.task(":$STOP_CORDA_TASK_NAME")!!.assertTaskSucceeded()
        }
    }
}
