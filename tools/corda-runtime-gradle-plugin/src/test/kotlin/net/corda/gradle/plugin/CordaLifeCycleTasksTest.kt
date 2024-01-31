package net.corda.gradle.plugin

import kotlinx.coroutines.*
//import net.corda.craft5.annotations.TestSuite
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.cordalifecycle.START_CORDA_TASK_NAME
import net.corda.gradle.plugin.cordalifecycle.STOP_CORDA_TASK_NAME
import net.corda.gradle.plugin.cordalifecycle.CLUSTER_TASKS_GROUP

import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

//@TestSuite
class CordaLifeCycleTasksTest : FunctionalBaseTest() {
    @OptIn(DelicateCoroutinesApi::class)
    @Test fun startAndStopCorda() {
        appendCordaRuntimeGradlePluginExtension()
        lateinit var result: BuildResult
        runBlocking {
            GlobalScope.launch {
                executeWithRunner(START_CORDA_TASK_NAME)
            }
            val job = GlobalScope.launch {
                delay(50000)
                result = executeWithRunner(STOP_CORDA_TASK_NAME)
            }
            job.join()
            result.task(":$STOP_CORDA_TASK_NAME")!!.assertTaskSucceeded()
        }
    }
    @Test
    fun shouldContainCordaRuntimePluginTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", CLUSTER_TASKS_GROUP).tasks)
    }

    @Test
    fun shouldFailToStopCorda() {
        assertTrue(executeAndFailWithRunner(STOP_CORDA_TASK_NAME).output.contains(CordaRuntimeGradlePluginException::class.java.name))
        assertTrue(
            Files.notExists(Path.of(projectDir.absolutePath + "/CordaPIDCache.dat")),
            "The process cache file is present but should be missing"
        )
    }
}
