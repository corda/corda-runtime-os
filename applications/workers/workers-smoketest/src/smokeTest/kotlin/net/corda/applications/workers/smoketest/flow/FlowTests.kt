package net.corda.applications.workers.smoketest.flow

import java.util.UUID
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_FAILED
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.addSoftHsmFor
import net.corda.applications.workers.smoketest.awaitMultipleRpcFlowFinished
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.createKeyFor
import net.corda.applications.workers.smoketest.createVirtualNodeFor
import net.corda.applications.workers.smoketest.getFlowClasses
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getRpcFlowResult
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder

@Suppress("Unused")
@Order(20)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests {

    companion object {

        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
        var charlieHoldingId: String = getHoldingIdShortHash(X500_CHARLIE, GROUP_ID)
        var davidHoldingId: String = getHoldingIdShortHash(X500_DAVID, GROUP_ID)

        val expectedFlows = listOf(
            "net.cordapp.flowworker.development.flows.MessagingFlow",
            "net.cordapp.flowworker.development.flows.PersistenceFlow",
            "net.cordapp.flowworker.development.flows.ReturnAStringFlow",
            "net.cordapp.flowworker.development.flows.RpcSmokeTestFlow",
            "net.cordapp.flowworker.development.flows.TestFlow",
            "net.cordapp.flowworker.development.flows.BrokenProtocolFlow"
        )

        /*
         * when debugging if you want to run the tests multiple times comment out the @BeforeAll
         * attribute to disable the vnode creation after the first run.
         */
        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {

            val bobActualHoldingId = createVirtualNodeFor(X500_BOB)
            val charlieActualHoldingId = createVirtualNodeFor(X500_CHARLIE)
            val davidActualHoldingId = createVirtualNodeFor(X500_DAVID)

            registerMember(bobActualHoldingId)
            registerMember(charlieActualHoldingId)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)

            addSoftHsmFor(bobHoldingId, "LEDGER")
        }
    }

    @Test
    fun `start RPC flow`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("echo")
        assertThat(flowResult.result).isEqualTo("hello")
    }

    @Test
    fun `start multiple RPC flow and validate they complete`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        startRpcFlow(charlieHoldingId, requestBody)
        startRpcFlow(davidHoldingId, requestBody)
        startRpcFlow(davidHoldingId, requestBody)

        awaitMultipleRpcFlowFinished(charlieHoldingId, 1)
        awaitMultipleRpcFlowFinished(davidHoldingId, 2)
    }

    @Test
    fun `start RPC flow twice, second returns an error code of 409`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        startRpcFlow(bobHoldingId, requestBody, 200)
        startRpcFlow(bobHoldingId, requestBody, 409)
    }

    @Test
    fun `start RPC flow - flow failure test`() {
        // 1) Start the flow but signal it to throw an exception when it starts
        val requestBody = RpcSmokeTestInput().apply {
            command = "throw_error"
            data = mapOf("error_message" to "oh no!")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        // 3) check the flow completes as expected
        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
        assertThat(result.flowResult).isNull()
        assertThat(result.flowError).isNotNull
        assertThat(result.flowError?.type).isEqualTo("FLOW_FAILED")
        assertThat(result.flowError?.message).isEqualTo("oh no!")
    }

    @Test
    fun `Init Session - initiate two sessions`() {

        val requestBody = RpcSmokeTestInput().apply {
            command = "start_sessions"
            data = mapOf(
                "sessions" to "${X500_BOB};${X500_CHARLIE}",
                "messages" to "m1;m2"
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("start_sessions")
        assertThat(flowResult.result)
            .isEqualTo("${X500_BOB}=echo:m1; ${X500_CHARLIE}=echo:m2")
    }

    /**
     * This test is failing unexpectedly, a bug has been raised to investigate
     * https://r3-cev.atlassian.net/browse/CORE-5372
     */
    @Test
    @Disabled
    fun `Platform Error - user code receives platform errors`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "throw_platform_error"
            data = mapOf("x500" to X500_BOB)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.command).isEqualTo("throw_platform_error")
        assertThat(flowResult.result).isEqualTo("type")
    }

    @Test
    fun `Persistence - insert a record`() {
        val id = UUID.randomUUID()
        val requestBody = RpcSmokeTestInput().apply {
            command = "persist_insert"
            data = mapOf("id" to id.toString())
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.command).isEqualTo("persist_insert")
        assertThat(flowResult.result).isEqualTo("dog '${id}' saved")
    }

    @Test
    fun `Flow persistence`() {
        val id = UUID.randomUUID()

        // Insert a dog
        var requestBody = RpcSmokeTestInput().apply {
            command = "persist_insert"
            data = mapOf("id" to id.toString())
        }

        var requestId = startRpcFlow(bobHoldingId, requestBody)

        var result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        var flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' saved")

        // Update a dog
        val newDogName = "dog2"
        requestBody = RpcSmokeTestInput().apply {
            command = "persist_update"
            data = mapOf(
                "id" to id.toString(),
                "name" to newDogName,
            )
        }

        requestId = startRpcFlow(bobHoldingId, requestBody)

        result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' updated")

        // find a dog
        requestBody = RpcSmokeTestInput().apply {
            command = "persist_find"
            data = mapOf(
                "id" to id.toString()
            )
        }

        requestId = startRpcFlow(bobHoldingId, requestBody)

        result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("found dog id='${id}' name='${newDogName}")

        // find all dogs
        requestBody = RpcSmokeTestInput().apply {
            command = "persist_findall"
        }

        requestId = startRpcFlow(bobHoldingId, requestBody)

        result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("found one or more dogs")

        // delete a dog
        requestBody = RpcSmokeTestInput().apply {
            command = "persist_delete"
            data = mapOf(
                "id" to id.toString()
            )
        }

        requestId = startRpcFlow(bobHoldingId, requestBody)

        result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' deleted")
    }

    @Test
    fun `Get runnable flows for a holdingId`() {
        val flows = getFlowClasses(bobHoldingId)

        assertThat(flows.size).isEqualTo(expectedFlows.size)
        assertTrue(flows.containsAll(expectedFlows))
    }

    @Test
    fun `SubFlow - Create an initiated session in an initiating flow and pass it to a inline subflow`() {

        val requestBody = RpcSmokeTestInput().apply {
            command = "subflow_passed_in_initiated_session"
            data = mapOf(
                "sessions" to "${X500_BOB};${X500_CHARLIE}",
                "messages" to "m1;m2"
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("subflow_passed_in_initiated_session")
        assertThat(flowResult.result)
            .isEqualTo("${X500_BOB}=echo:m1; ${X500_CHARLIE}=echo:m2")
    }

    @Test
    fun `SubFlow - Create an uninitiated session in an initiating flow and pass it to a inline subflow`() {

        val requestBody = RpcSmokeTestInput().apply {
            command = "subflow_passed_in_non_initiated_session"
            data = mapOf(
                "sessions" to "${X500_BOB};${X500_CHARLIE}",
                "messages" to "m1;m2"
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("subflow_passed_in_non_initiated_session")
        assertThat(flowResult.result)
            .isEqualTo("${X500_BOB}=echo:m1; ${X500_CHARLIE}=echo:m2")
    }

    @Test
    fun `Crypto - Sign and verify bytes`() {

        val publicKey = createKeyFor(bobHoldingId, UUID.randomUUID().toString(), "LEDGER", "CORDA.RSA")

        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_sign_and_verify"
            data = mapOf("publicKey" to publicKey)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("crypto_sign_and_verify")
        assertThat(flowResult.result).isEqualTo(true.toString())
    }

    @Test
    fun `Crypto - Verify invalid signature`() {

        val publicKey = createKeyFor(bobHoldingId, UUID.randomUUID().toString(), "LEDGER", "CORDA.RSA")

        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_verify_invalid_signature"
            data = mapOf("publicKey" to publicKey)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("crypto_verify_invalid_signature")
        assertThat(flowResult.result).isEqualTo(true.toString())
    }
}
