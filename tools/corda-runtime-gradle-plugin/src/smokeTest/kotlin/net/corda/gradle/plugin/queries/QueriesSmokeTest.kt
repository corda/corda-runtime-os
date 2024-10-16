package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.CombinedWorkerHelper.startCompose
import net.corda.gradle.plugin.CombinedWorkerHelper.stopCompose
import net.corda.gradle.plugin.SmokeTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class QueriesSmokeTest : SmokeTestBase() {
    companion object {
        @JvmStatic
        @BeforeAll
        fun startCombinedWorker() {
            startCompose()
        }

        @JvmStatic
        @AfterAll
        fun stopCombinedWorker() {
            stopCompose()
        }
    }

    @Test
    fun listVNodesIsSuccessful() {
        val result = executeWithRunner(LIST_VNODES_TASK_NAME, "--info")
        assertThat(result.output)
            .containsPattern("CPI Name\\s+Holding identity short hash\\s+X500 Name")
    }

    @Test
    fun listCPIsIsSuccessful() {
        val result = executeWithRunner(LIST_CPIS_TASK_NAME)
        assertThat(result.output)
            .containsPattern("CpiName\\s+CpiVersion\\s+CpiFileCheckSum")
    }
}
