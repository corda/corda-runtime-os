package net.corda.applications.workers.smoketest.flow

import java.util.UUID
import kotlin.text.Typography.quote
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.configWithDefaultsNode
import net.corda.applications.workers.smoketest.getConfig
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.toJsonString
import net.corda.applications.workers.smoketest.updateConfig
import net.corda.applications.workers.smoketest.waitForConfigurationChange
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder

@Suppress("Unused", "FunctionName")
@Order(20)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests {

    companion object {
        private val testRunUniqueId = UUID.randomUUID()
        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, GROUP_ID)
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
        private val davidX500 = "CN=David-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var davidHoldingId: String = getHoldingIdShortHash(davidX500, GROUP_ID)
        private val charlyX500 = "CN=Charley-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var charlieHoldingId: String = getHoldingIdShortHash(charlyX500, GROUP_ID)
        private val staticMemberList = listOf(
            aliceX500,
            bobX500,
            charlyX500,
            davidX500
        )

        val invalidConstructorFlowNames = listOf(
            "net.cordapp.testing.smoketests.flow.errors.PrivateConstructorFlow",
            "net.cordapp.testing.smoketests.flow.errors.PrivateConstructorJavaFlow",
            "net.cordapp.testing.smoketests.flow.errors.NoDefaultConstructorFlow",
            "net.cordapp.testing.smoketests.flow.errors.NoDefaultConstructorJavaFlow",
        )

        val dependencyInjectionFlowNames = listOf(
            "net.cordapp.testing.smoketests.flow.DependencyInjectionTestFlow",
            "net.cordapp.testing.smoketests.flow.inheritance.DependencyInjectionTestJavaFlow",
        )

        val expectedFlows = listOf(
            "net.cordapp.testing.smoketests.virtualnode.ReturnAStringFlow",
            "net.cordapp.testing.smoketests.virtualnode.SimplePersistenceCheckFlow",
            "net.cordapp.testing.smoketests.flow.AmqpSerializationTestFlow",
            "net.cordapp.testing.smoketests.flow.RpcSmokeTestFlow",
            "net.cordapp.testing.testflows.TestFlow",
            "net.cordapp.testing.testflows.BrokenProtocolFlow",
            "net.cordapp.testing.testflows.MessagingFlow",
            "net.cordapp.testing.testflows.PersistenceFlow",
            "net.cordapp.testing.testflows.UniquenessCheckTestFlow"
        ) + invalidConstructorFlowNames + dependencyInjectionFlowNames

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(cpiName, TEST_CPB_LOCATION, GROUP_ID, staticMemberList)

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
            val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlyX500, cpiName)
            val davidActualHoldingId = getOrCreateVirtualNodeFor(davidX500, cpiName)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)

            registerMember(bobHoldingId)
            registerMember(charlieHoldingId)
        }
    }

    /**
     * Removes whitespaces unless they are in quotes, allowing Json declared in tests to take any shape and still pass
     * string matching with expected outputs from Flows.
     */
    private fun String.trimJson(): String {
        var isInQuotes = false
        return this.filter { char ->
            if (char == quote) isInQuotes = !isInQuotes
            !char.isWhitespace() || isInQuotes
        }
    }

    @Test
    fun `cluster configuration changes are picked up and workers continue to operate normally`() {
        val currentConfigValue = getConfig(MESSAGING_CONFIG).configWithDefaultsNode()[MAX_ALLOWED_MSG_SIZE].asInt()
        val newConfigurationValue = (currentConfigValue * 1.5).toInt()

        // Update cluster configuration (ConfigProcessor should kick off on all workers at this point)
        updateConfig(mapOf(MAX_ALLOWED_MSG_SIZE to newConfigurationValue).toJsonString(), MESSAGING_CONFIG)

        // Wait for the rpc-worker to reload the configuration and come back up
        waitForConfigurationChange(MESSAGING_CONFIG, MAX_ALLOWED_MSG_SIZE, newConfigurationValue.toString())

        try {
            // Execute some flows which require functionality from different workers and make sure they succeed
            val flowIds = mutableListOf(
                startRpcFlow(
                    bobHoldingId,
                    RpcSmokeTestInput().apply {
                        command = "persistence_persist"
                        data = mapOf("id" to UUID.randomUUID().toString())
                    }
                ),

                startRpcFlow(
                    bobHoldingId,
                    RpcSmokeTestInput().apply {
                        command = "crypto_sign_and_verify"
                        data = mapOf("memberX500" to bobX500)
                    }
                ),

                startRpcFlow(
                    bobHoldingId,
                    RpcSmokeTestInput().apply {
                        command = "lookup_member_by_x500_name"
                        data = mapOf("id" to charlyX500)
                    }
                )
            )

            flowIds.forEach {
                val flowResult = awaitRpcFlowFinished(bobHoldingId, it)
                assertThat(flowResult.flowError).isNull()
                assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            }
        } finally {
            // Be a good neighbour and rollback the configuration change back to what it was
            updateConfig(mapOf(MAX_ALLOWED_MSG_SIZE to currentConfigValue).toJsonString(), MESSAGING_CONFIG)
            waitForConfigurationChange(MESSAGING_CONFIG, MAX_ALLOWED_MSG_SIZE, currentConfigValue.toString())
        }
    }
}
