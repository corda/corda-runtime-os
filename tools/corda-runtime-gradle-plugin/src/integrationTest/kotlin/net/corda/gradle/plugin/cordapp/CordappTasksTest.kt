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
    fun groupPolicyIsGenerated() {
        val groupPolicyFile = projectDir.resolve("workspace").resolve("GroupPolicy.json")
        require(!groupPolicyFile.exists()) { "Group policy file $groupPolicyFile should not exist" }

        appendCordaRuntimeGradlePluginExtension()
        executeWithRunner(CREATE_GROUP_POLICY_TASK_NAME)
        assertTrue(groupPolicyFile.isFile)
    }

    @Test
    fun shouldFailBuildCpiWithNoWorkflows() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(BUILD_CPIS_TASK_NAME)
        assertTrue(result.output.contains("Task with path ':workflowsModule:build' not found"))
    }
}
