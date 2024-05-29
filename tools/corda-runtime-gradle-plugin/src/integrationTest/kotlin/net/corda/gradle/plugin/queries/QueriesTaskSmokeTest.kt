package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.FunctionalBaseTest
import net.corda.gradle.plugin.TestExecutionConditions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf

@EnabledIf("isRestApiReachable")
class QueriesTaskSmokeTest : FunctionalBaseTest() {
    companion object {
        @JvmStatic
        fun isRestApiReachable() = TestExecutionConditions.isRestApiReachable()
    }

    @Test
    fun listVNodesIsSuccessful() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_VNODES_TASK_NAME)
        assertThat(result.output)
            .containsPattern("CPI Name\\s+Holding identity short hash\\s+X500 Name")
    }

    @Test
    fun listCPIsIsSuccessful() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_CPIS_TASK_NAME)
        assertThat(result.output)
            .containsPattern("CpiName\\s+CpiVersion\\s+CpiFileCheckSum")
    }
}
