package net.corda.gradle.plugin

//import net.corda.craft5.annotations.TestSuite
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.cordalifecycle.START_CORDA_TASK_NAME
import net.corda.gradle.plugin.cordalifecycle.STOP_CORDA_TASK_NAME
import net.corda.gradle.plugin.cordalifecycle.CLUSTER_TASKS_GROUP

import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path

//@TestSuite
class CordaLifeCycleTasksTest : FunctionalBaseTest() {
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "No docker runtime in CI test env")
    fun startAndStopCorda() {
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
    @EnabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "Docker is expected to be running in local env")
    fun shouldFailToStartCordaOnCiWithoutDocker() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(START_CORDA_TASK_NAME)
        assertTrue(result.output.contains(CordaRuntimeGradlePluginException::class.java.name))
        assertTrue(result.output.contains("Cannot connect to the Docker daemon"))
    }

    @Test
    fun shouldContainCordaRuntimePluginTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", CLUSTER_TASKS_GROUP).tasks)
    }

    @Test
    fun shouldFailToStopCorda() {
        assertTrue(executeAndFailWithRunner(STOP_CORDA_TASK_NAME).output.contains(CordaRuntimeGradlePluginException::class.java.name))
        assertTrue(
            Files.notExists(Path.of(projectDir.absolutePath + "/workspace/CordaPIDCache.dat")),
            "The process cache file is present but should be missing"
        )
    }
}
