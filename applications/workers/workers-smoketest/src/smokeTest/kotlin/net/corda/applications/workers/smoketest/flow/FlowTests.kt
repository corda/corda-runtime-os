package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.X500_BOB
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder
import java.util.*

@Suppress("Unused")
@Order(20)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests {

    companion object {

        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)

        /*
         * when debugging if you want to run the tests multiple times comment out the @BeforeAll
         * attribute to disable the vnode creation after the first run.
         */
        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {

            val actualHoldingId = createVirtualNodeFor(X500_BOB)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(actualHoldingId).isEqualTo(bobHoldingId)

            createVirtualNodeFor(X500_SESSION_USER1)
            createVirtualNodeFor(X500_SESSION_USER2)
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
                "sessions" to "${X500_SESSION_USER1};${X500_SESSION_USER2}",
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
            .isEqualTo("${X500_SESSION_USER1}=echo:m1; ${X500_SESSION_USER2}=echo:m2")
    }

   /**
    * This test is failing unexpectedly, a bug has been raised to investigate
    * https://r3-cev.atlassian.net/browse/CORE-5372
    *
    @Test
    fun `Platform Error - user code receives platform errors`(){
        val requestBody = RpcSmokeTestInput().apply {
            command = "throw_platform_error"
            data = mapOf("x500" to X500_SESSION_USER1)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.command).isEqualTo("throw_platform_error")
        assertThat(flowResult.result).isEqualTo("type")
    }*/

    @Test
    fun `Persistence - insert a record`(){
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
    fun `Flow persistence`(){
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
}