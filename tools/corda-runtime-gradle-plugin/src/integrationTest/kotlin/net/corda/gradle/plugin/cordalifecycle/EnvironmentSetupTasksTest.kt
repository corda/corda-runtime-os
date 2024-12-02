package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.FunctionalBaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EnvironmentSetupTasksTest : FunctionalBaseTest() {
    @Test
    fun shouldContainSupportingTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", UTIL_TASK_GROUP).tasks)
    }

    @Test
    fun getNotaryTaskDoesExistWhenNonValidatingNotaryIsUSed() {
        appendCordaRuntimeGradlePluginExtension()
        val configFile = getNetworkConfigFile()
        val existingContent = configFile.readText()
        // check our setup is correct
        assertThat(existingContent).contains("nonvalidating")
        val utilTasks = executeWithRunner("tasks", "--group", UTIL_TASK_GROUP).output
        assertThat(utilTasks).contains(GET_NOTARY_SERVER_CPB_TASK_NAME)
    }

    @Test
    fun shouldDownloadNotaryCPB() {
        appendCordaRuntimeGradlePluginExtension(appendArtifactoryCredentials = true)
        executeWithRunner(GET_NOTARY_SERVER_CPB_TASK_NAME)
            .task(":$GET_NOTARY_SERVER_CPB_TASK_NAME")!!.assertTaskSucceeded()
    }

    @Test
    fun getNotaryTaskDoesNotExistWhenNotNonValidating() {
        appendCordaRuntimeGradlePluginExtension()
        val configFile = getNetworkConfigFile()
        val existingContent = configFile.readText()
        val newContent = existingContent.replace("nonvalidating", "contractverifying")
        configFile.writeText(newContent)
        val utilTasks = executeWithRunner("tasks", "--group", UTIL_TASK_GROUP).output
        assertThat(utilTasks).doesNotContain(GET_NOTARY_SERVER_CPB_TASK_NAME)
    }
}
