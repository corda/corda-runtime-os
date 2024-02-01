package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.FunctionalBaseTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueriesTasksTest : FunctionalBaseTest() {
    @Test
    fun shouldContainSupportingTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", CLUSTER_QUERY_GROUP).tasks)
    }

    @Test
    fun listVNodesFailsConnectionRefused() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(LISTVNODES_TASK_NAME)
        assertTrue(result.output.contains("Connect to $restHostnameWithPort"))
        assertTrue(result.output.contains("Connection refused"))
    }

    @Test
    fun listCPIsFailsConnectionRefused() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(LISTCPIS_TASK_NAME)
        assertTrue(result.output.contains("Connect to $restHostnameWithPort"))
        assertTrue(result.output.contains("Connection refused"))
    }
}
