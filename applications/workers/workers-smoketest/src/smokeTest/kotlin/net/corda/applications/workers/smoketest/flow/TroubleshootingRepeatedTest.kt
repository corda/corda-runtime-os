package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.getRpcFlowResult
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import java.util.*

@Disabled
class TroubleshootingRepeatedTest {
    companion object {
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
            "net.cordapp.testing.testflows.UniquenessCheckTestFlow",
            "net.cordapp.testing.testflows.ledger.ConsensualSignedTransactionSerializationFlow",
        ) + invalidConstructorFlowNames + dependencyInjectionFlowNames

    }

    @RepeatedTest(5)
    fun test() {
         val testRunUniqueId = UUID.randomUUID()
         val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
         val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
         var bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
         val davidX500 = "CN=David-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
         var davidHoldingId: String = getHoldingIdShortHash(davidX500, GROUP_ID)
         val charlyX500 = "CN=Charley-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
         var charlieHoldingId: String = getHoldingIdShortHash(charlyX500, GROUP_ID)
         val staticMemberList = listOf(
            bobX500,
            charlyX500,
            davidX500
        )

            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(cpiName, TEST_CPB_LOCATION, GROUP_ID, staticMemberList)

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
            val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlyX500, cpiName)
            val davidActualHoldingId = getOrCreateVirtualNodeFor(davidX500, cpiName)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            Assertions.assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            Assertions.assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            Assertions.assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)

            registerMember(bobHoldingId)
            registerMember(charlieHoldingId)

        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        Assertions.assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        Assertions.assertThat(result.flowError).isNull()
        Assertions.assertThat(flowResult.command).isEqualTo("echo")
        Assertions.assertThat(flowResult.result).isEqualTo("hello")

    }
}