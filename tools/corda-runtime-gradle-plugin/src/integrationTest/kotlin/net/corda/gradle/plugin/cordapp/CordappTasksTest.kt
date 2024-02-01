package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.FunctionalBaseTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CordappTasksTest : FunctionalBaseTest() {

    @Test
    fun shouldContainSupportingTasks() {
        assertTrue(executeWithRunner("tasks", "--group", CORDAPP_BUILD_GROUP).tasks.isNotEmpty())
    }

    @Test
    fun shouldFailCreateGroupPolicyWithNoCordaCli() {
        val result = executeAndFailWithRunner(CREATE_GROUP_POLICY_TASK_NAME)
        assertTrue(result.output.contains("Unable to find the Corda CLI, has it been installed?"))
    }

    @Test
    fun shouldFailBuildCpiWithNoWorkflows() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(BUILD_CPIS_TASK_NAME)
        assertTrue(result.output.contains("Task with path ':workflowsModule:build' not found"))
    }
}
