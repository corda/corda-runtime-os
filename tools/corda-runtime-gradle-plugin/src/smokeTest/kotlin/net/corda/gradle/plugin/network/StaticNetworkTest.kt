package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.CombinedWorkerHelper.startCompose
import net.corda.gradle.plugin.CombinedWorkerHelper.stopCompose
import net.corda.gradle.plugin.SmokeTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class StaticNetworkTest : SmokeTestBase() {
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
    fun vNodesSetupSucceeds() {
        appendCordaRuntimeGradlePluginExtension(appendArtifactoryCredentials = true)
        val result = executeWithRunner(VNODE_SETUP_TASK_NAME, "--info", forwardOutput = true)

        val vNodeRegisteredMessage = Regex("VNode .+ with shortHash [A-F0-9]+ registered.")
        assertThat(result.output).containsPattern(vNodeRegisteredMessage.pattern)

        val expectedCommonNames = setOf("Alice", "Bob", "Charlie", "Dave", "NotaryRep1")
        val registeredCommonNames = result.output.lines()
            .filter { it.matches(vNodeRegisteredMessage) }
            .map { it.split("CN=").last().split(",").first() }.toSet()
        assertThat(registeredCommonNames).isEqualTo(expectedCommonNames)
    }
}
