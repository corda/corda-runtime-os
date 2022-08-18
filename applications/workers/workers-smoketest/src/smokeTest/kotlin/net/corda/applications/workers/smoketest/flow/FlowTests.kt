package net.corda.applications.workers.smoketest.flow

import java.util.*
import net.corda.applications.workers.smoketest.*
import net.corda.craft5.annotations.TestSuite
import net.corda.craft5.common.millis
import net.corda.craft5.corda.client.*
import net.corda.craft5.http.Http
import net.corda.craft5.terminal.Terminal
import net.corda.craft5.util.retry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance.Lifecycle


@Suppress("Unused")
@Order(20)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
@TestSuite
class FlowTests {

    companion object {

        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
        var charlieHoldingId: String = getHoldingIdShortHash(X500_CHARLIE, GROUP_ID)
        var davidHoldingId: String = getHoldingIdShortHash(X500_DAVID, GROUP_ID)

        val expectedFlows = listOf(
            "net.cordapp.flowworker.development.smoketests.virtualnode.ReturnAStringFlow",
            "net.cordapp.flowworker.development.smoketests.flow.RpcSmokeTestFlow",
            "net.cordapp.flowworker.development.smoketests.flow.errors.NoValidConstructorFlow",
            "net.cordapp.flowworker.development.testflows.TestFlow",
            "net.cordapp.flowworker.development.testflows.BrokenProtocolFlow",
            "net.cordapp.flowworker.development.testflows.MessagingFlow",
            "net.cordapp.flowworker.development.testflows.PersistenceFlow"
        )

        private lateinit var cordaClient: CordaClient

        /*
         * when debugging if you want to run the tests multiple times comment out the @BeforeAll
         * attribute to disable the vnode creation after the first run.
         */
        @BeforeAll
        @JvmStatic
        internal fun beforeAll(clientTerminal: Terminal, httpClient: Http) {
            cordaClient = CordaClientImpl(clientTerminal, httpClient)
            cordaClient.setParams(CLUSTER_URI, USERNAME, PASSWORD)

            val bobActualHoldingId = createVirtualNodeFor(cordaClient, X500_BOB)
            val charlieActualHoldingId = createVirtualNodeFor(cordaClient, X500_CHARLIE)
            val davidActualHoldingId = createVirtualNodeFor(cordaClient, X500_DAVID)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)

            cordaClient.registerMember(bobHoldingId, "CORDA.ECDSA.SECP256R1")
            cordaClient.registerMember(charlieHoldingId, "CORDA.ECDSA.SECP256R1")
        }
    }

    @Test
    fun `start RPC flow`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId
        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowError).isNull()
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.command).isEqualTo("echo")
        assertThat(flowResult.result).isEqualTo("hello")
    }

    @Test
    fun `start multiple RPC flow and validate they complete`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }
        cordaClient.startFlow(davidHoldingId, SMOKE_TEST_CLASS_NAME, requestBody)
        cordaClient.startFlow(davidHoldingId, SMOKE_TEST_CLASS_NAME, requestBody)
        cordaClient.awaitMultipleFlowFinished(davidHoldingId, 2, cooldown = 500.millis)
    }

    @Test
    fun `start RPC flow twice, second returns an error code of 409`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }
        val requestId = "123"
        val firstFlow = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody, requestId)
        assertEquals(200, firstFlow.getResponseObject().status(), "Expected the first flow to start successfully")

        // Send second request with same clientRequestId and expect an error
        // need to retry as we may get a "START_REQUESTED" or "RUNNING" status before it figures out it should error
        retry(attempts = 5) {
            val secondFlow = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody, requestId)
            assertEquals(409, secondFlow.getResponseObject().status(), "Expected second flow to return 409 error")
        }
    }

    @Test
    fun `start RPC flow - flow failure test`() {
        // 1) Start the flow but signal it to throw an exception when it starts
        val requestBody = RpcSmokeTestInput().apply {
            command = "throw_error"
            data = mapOf("error_message" to "oh no!")
        }

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        // 3) check the flow completes as expected
        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
        assertThat(result.flowResult).isNull()
        assertThat(result.flowError).isNotNull
        val flowError = result.flowError.toString()
        assertTrue("type=FLOW_FAILED" in flowError)
        assertTrue("oh no!" in flowError)
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

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        val flowResult = result.getRpcFlowResult()
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

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.command).isEqualTo("throw_platform_error")
        assertThat(flowResult.result).isEqualTo("type")
    }

    @Test
    fun `Pipeline error results in flow marked as failed`() {
        val requestId = cordaClient.startFlow(bobHoldingId,
            "net.cordapp.flowworker.development.smoketests.flow.errors.NoValidConstructorFlow",
                mapOf()
            ).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
    }

    @Test
    fun `Persistence - insert a record`() {
        val id = UUID.randomUUID()
        val requestBody = RpcSmokeTestInput().apply {
            command = "persist_insert"
            data = mapOf("id" to id.toString())
        }

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

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

        var requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        var result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

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

        requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

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

        requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("found dog id='${id}' name='${newDogName}")

        // find all dogs
        requestBody = RpcSmokeTestInput().apply {
            command = "persist_findall"
        }

        requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

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

        requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' deleted")
    }

    @Test
    fun `Get runnable flows for a holdingId`() {
        val flows = cordaClient.listStartableFlows(bobHoldingId).flowClassNames

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

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        val flowResult = result.getRpcFlowResult()
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

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.command).isEqualTo("subflow_passed_in_non_initiated_session")
        assertThat(flowResult.result)
            .isEqualTo("${X500_BOB}=echo:m1; ${X500_CHARLIE}=echo:m2")
    }

    @Test
    fun `Crypto - Sign and verify bytes`() {

        val publicKey = createKeyFor(cordaClient, bobHoldingId, UUID.randomUUID().toString(), "LEDGER", "CORDA.RSA")

        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_sign_and_verify"
            data = mapOf("publicKey" to publicKey)
        }

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.command).isEqualTo("crypto_sign_and_verify")
        assertThat(flowResult.result).isEqualTo(true.toString())
    }

    @Test
    fun `Crypto - Verify invalid signature`() {

        val publicKey = createKeyFor(cordaClient, bobHoldingId, UUID.randomUUID().toString(), "LEDGER", "CORDA.RSA")

        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_verify_invalid_signature"
            data = mapOf("publicKey" to publicKey)
        }

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.command).isEqualTo("crypto_verify_invalid_signature")
        assertThat(flowResult.result).isEqualTo(true.toString())
    }

    @Test
    fun `Context is propagated to initiated and sub flows`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "context_propagation"
        }

        val requestId = cordaClient.startFlow(bobHoldingId, SMOKE_TEST_CLASS_NAME, requestBody).clientRequestId

        val result = cordaClient.awaitFlowFinish(bobHoldingId, requestId, cooldown = 500.millis)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("context_propagation")

        val CONTEXT_JSON =
            """
            {
              "rpcFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "null"
              },
              "rpcSubFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "user2-set"
              },
              "initiatedFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "user2-set"
              },
              "initiatedSubFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "user2-set-ContextPropagationInitiatedFlow"
              },
              "rpcFlowAtComplete": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "null"
              }
            }
            """.filter { !it.isWhitespace() }

        assertThat(flowResult.result)
            .isEqualTo(CONTEXT_JSON)
    }
}
