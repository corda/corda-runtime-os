package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.FunctionalBaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CordappTasksTest : FunctionalBaseTest() {

    @Test
    fun shouldContainSupportingTasks() {
        assertTrue(executeWithRunner("tasks", "--group", CORDAPP_BUILD_GROUP).tasks.isNotEmpty())
    }

    @Test
    fun groupPolicyIsGeneratedForStaticNetwork() {
        val groupPolicyFile = projectDir.resolve("workspace").resolve("GroupPolicy.json")
        groupPolicyFile.delete()
        require(!groupPolicyFile.exists()) { "Group policy file $groupPolicyFile should not exist" }

        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = true)
        executeWithRunner(CREATE_GROUP_POLICY_TASK_NAME)
        assertThat(groupPolicyFile).exists().isFile
    }

    @Test
    fun groupPolicyIsNotGeneratedForDynamicNetwork() {
        val groupPolicyFile = projectDir.resolve("workspace").resolve("GroupPolicy.json")
        groupPolicyFile.delete()
        require(!groupPolicyFile.exists()) { "Group policy file $groupPolicyFile should not exist" }

        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = false)
        executeWithRunner(CREATE_GROUP_POLICY_TASK_NAME)
        assertThat(groupPolicyFile).doesNotExist()

    }

    @Test
    fun shouldFailBuildCpiWithNoWorkflows() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = false)
        val result = executeAndFailWithRunner(BUILD_CPIS_TASK_NAME)
        assertTrue(result.output.contains("Task with path ':workflowsModule:build' not found"))
    }

    @Test
    fun shouldFailDeployMgmIfStaticNetwork() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = true)
        val result = executeAndFailWithRunner(DEPLOY_MGMS_TASK_NAME)
        assertTrue(result.output.contains("An MGM must be included in the network definition"))
    }
}
