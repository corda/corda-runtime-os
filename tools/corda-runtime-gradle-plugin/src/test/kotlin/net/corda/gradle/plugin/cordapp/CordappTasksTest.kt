package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.FunctionalBaseTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CordappTasksTest : FunctionalBaseTest() {

    @Test
    fun shouldContainSupportingTasks() {
        assertTrue(executeWithRunner("tasks", "--group", CORDAPP_BUILD_GROUP).tasks.isNotEmpty())
    }

    @Test
    fun shouldCreateGroupPolicy() {
        executeWithRunner(CREATE_GROUP_POLICY_TASK_NAME).task(":$CREATE_GROUP_POLICY_TASK_NAME")!!.assertTaskSucceeded()
        assertTrue(
            Files.exists(Path.of(projectDir.absolutePath + "/workspace/GroupPolicy.json")),
            "The group policy file should be created"
        )
    }

    @Test
    fun shouldCreateKeyStore() {
        executeWithRunner(CREATE_KEYSTORE_TASK_NAME)
            .task(":$CREATE_GROUP_POLICY_TASK_NAME")!!.assertTaskSucceeded()
        assertTrue(
            Files.exists(Path.of(projectDir.absolutePath + "/workspace/signingkeys.pfx")),
            "The keystore file should be created"
        )
    }

    @Test
    fun shouldFailBuildCpiWithNoWorkflows() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(BUILD_CPIS_TASK_NAME)
        assertTrue(result.output.contains("Task with path ':workflowsModule:build' not found"))
    }
}
