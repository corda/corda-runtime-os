package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.FunctionalBaseTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkTasksTest : FunctionalBaseTest() {

    @Test
    fun shouldContainSupportingTasks() {
        assertTrue(executeWithRunner("tasks", "--group", NETWORK_GROUP).tasks.isNotEmpty())
    }

    @Test
    fun shouldFailVNodeSetupWithNoWorkflows() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(VNODE_SETUP_TASK_NAME)
        assertTrue(result.output.contains("Task with path ':workflowsModule:build' not found"))
    }
}
