package net.corda.gradle.plugin.cordalifecycle

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corda.gradle.plugin.EndToEndTestBase
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Test

class CordaLifeCycleTasksTest : EndToEndTestBase() {
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun startAndStopCorda() {
        appendCordaRuntimeGradlePluginExtension()
        lateinit var result: BuildResult
        runBlocking {
            GlobalScope.launch {
                executeWithRunner(START_CORDA_TASK_NAME, forwardOutput = true)
            }
            val job = GlobalScope.launch {
                waitUntilRestApiIsAvailable()
                result = executeWithRunner(STOP_CORDA_TASK_NAME)
            }
            job.join()
            result.task(":$STOP_CORDA_TASK_NAME")!!.assertTaskSucceeded()
        }
    }
}
