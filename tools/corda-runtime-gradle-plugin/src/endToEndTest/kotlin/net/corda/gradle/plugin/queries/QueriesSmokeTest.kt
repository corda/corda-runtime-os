package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.EndToEndTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QueriesSmokeTest : EndToEndTestBase() {
    @Test
    fun listVNodesIsSuccessful() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_VNODES_TASK_NAME, "--info")
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
