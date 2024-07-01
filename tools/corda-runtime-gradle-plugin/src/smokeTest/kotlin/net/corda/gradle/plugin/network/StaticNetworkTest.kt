package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.CombinedWorkerHelper.restClient
import net.corda.gradle.plugin.CombinedWorkerHelper.startCompose
import net.corda.gradle.plugin.CombinedWorkerHelper.stopCompose
import net.corda.gradle.plugin.SmokeTestBase
import net.corda.gradle.plugin.queries.LIST_VNODES_TASK_NAME
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
    fun setupNetworkVerifyVNodesAndCPIs() {
        // Create a static network
        val vNodeSetupResult = executeWithRunner(
            VNODE_SETUP_TASK_NAME,
            "--info", "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = true
        )

        val vNodeRegisteredMessage = Regex("VNode .+ with shortHash [A-F0-9]+ registered.")
        assertThat(vNodeSetupResult.output).containsPattern(vNodeRegisteredMessage.pattern)

        val expectedCommonNames = listOf("Alice", "Bob", "Charlie", "Dave", "NotaryRep1")
        val registeredVNodes = vNodeSetupResult.output.lines().filter { it.matches(vNodeRegisteredMessage) }.associate {
            val commonName = it.split("CN=").last().split(",").first()
            val shortHash = it.split(" with shortHash ").last().split(" registered.").first()
            commonName to shortHash
        }
        assertThat(registeredVNodes.keys).containsExactlyInAnyOrderElementsOf(expectedCommonNames)

        // List VNodes and verify output
        val listVNodesResult = executeWithRunner(
            LIST_VNODES_TASK_NAME,
            "--info",
            "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = true
        )

        val vNodes = listVNodesResult.output.lines().filter { it.contains("CN=") }.map {
            val (cpiName, shortHash) = it.split(Regex("\\s+")).take(2)
            val commonName = it.split("CN=").last().split(",").first()
            Triple(cpiName, shortHash, commonName)
        }
        assertThat(vNodes).hasSameSizeAs(expectedCommonNames)

        val expectedCpiNames = listOf("MyCorDapp", "NotaryServer")
        assertThat(vNodes.map { it.first }.toSet()).containsExactlyInAnyOrderElementsOf(expectedCpiNames)
        assertThat(vNodes.map { it.second }).containsExactlyInAnyOrderElementsOf(registeredVNodes.values)
        assertThat(vNodes.map { it.third }).containsExactlyInAnyOrderElementsOf(expectedCommonNames)

        // Verify startable flows
        val myCorDappFlows = restClient.flowInfoClient.getFlowclassHoldingidentityshorthash(registeredVNodes["Alice"]!!)
        assertThat(myCorDappFlows.flowClassNames).isEmpty()

        val notaryCpks = restClient.cpiClient.getCpi().cpis.filter { it.id.cpiName == "NotaryServer" }.firstOrNull()?.cpks
        assertThat(notaryCpks).isNotNull
        assertThat(notaryCpks).isNotEmpty
        assertThat(notaryCpks).allSatisfy { assertThat(it.id.name).contains("com.r3.corda.notary") }
    }
}
