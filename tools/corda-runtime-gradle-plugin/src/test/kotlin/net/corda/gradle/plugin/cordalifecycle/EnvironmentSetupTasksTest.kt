package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.FunctionalBaseTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class EnvironmentSetupTasksTest : FunctionalBaseTest() {
    @Test
    fun shouldContainSupportingTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", UTIL_TASK_GROUP).tasks)
    }

    @Test
    @Disabled
    fun shouldDownloadNotaryCPB() {
        appendCordaRuntimeGradlePluginExtension()
//        executeWithRunner(GET_NOTARY_SERVER_CPB_TASK_NAME)
//            .task(":$GET_NOTARY_SERVER_CPB_TASK_NAME")!!.assertTaskSucceeded()
    }

    @Test
    fun shouldGetCombinedWorker() {
        appendCordaRuntimeGradlePluginExtension()
        executeWithRunner(GET_COMBINED_WORKER_JAR_TASK_NAME)
            .task(":$GET_COMBINED_WORKER_JAR_TASK_NAME")!!.assertTaskSucceeded()
    }
}
