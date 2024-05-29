package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.SmokeTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class QueriesSmokeTest : SmokeTestBase() {
    @Test
    @Disabled("Tests to be added by https://r3-cev.atlassian.net/browse/ES-2344")
    fun `fake test`() {}

    @Test
    fun `list vNodes is successful`() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_VNODES_TASK_NAME)
        assertThat(result.output)
            .containsPattern("CPI Name\\s+Holding identity short hash\\s+X500 Name")
    }

    @Test
    fun `list CPIs is successful`() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeWithRunner(LIST_CPIS_TASK_NAME)
        assertThat(result.output)
            .containsPattern("CpiName\\s+CpiVersion\\s+CpiFileCheckSum")
    }
}
