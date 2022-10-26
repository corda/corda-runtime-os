package net.corda.applications.workers.smoketest.flow

import java.util.UUID
import net.corda.applications.workers.smoketest.FlowStatus
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_FAILED
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.X500_CHARLIE
import net.corda.applications.workers.smoketest.X500_DAVID
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.conditionallyUploadCordaPackage
import net.corda.applications.workers.smoketest.configWithDefaultsNode
import net.corda.applications.workers.smoketest.getConfig
import net.corda.applications.workers.smoketest.getFlowClasses
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.getOrCreateVirtualNodeFor
import net.corda.applications.workers.smoketest.getRpcFlowResult
import net.corda.applications.workers.smoketest.registerMember
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.TEST_STATIC_MEMBER_LIST
import net.corda.applications.workers.smoketest.toJsonString
import net.corda.applications.workers.smoketest.updateConfig
import net.corda.applications.workers.smoketest.waitForConfigurationChange
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
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
        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
        var davidHoldingId: String = getHoldingIdShortHash(X500_DAVID, GROUP_ID)
        var charlieHoldingId: String = getHoldingIdShortHash(X500_CHARLIE, GROUP_ID)

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
            "net.cordapp.testing.smoketests.flow.RpcSmokeTestFlow",
            "net.cordapp.testing.testflows.TestFlow",
            "net.cordapp.testing.testflows.BrokenProtocolFlow",
            "net.cordapp.testing.testflows.MessagingFlow",
            "net.cordapp.testing.testflows.PersistenceFlow",
            "net.cordapp.testing.testflows.UniquenessCheckTestFlow",
            "net.cordapp.testing.testflows.ledger.ConsensualSignedTransactionSerializationFlow",
        ) + invalidConstructorFlowNames + dependencyInjectionFlowNames

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(TEST_CPI_NAME, TEST_CPB_LOCATION, GROUP_ID, TEST_STATIC_MEMBER_LIST)

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(X500_BOB)
            val charlieActualHoldingId = getOrCreateVirtualNodeFor(X500_CHARLIE)
            val davidActualHoldingId = getOrCreateVirtualNodeFor(X500_DAVID)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)

            registerMember(bobHoldingId)
            registerMember(charlieHoldingId)
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

        val flowIds = mutableListOf(
            startRpcFlow(davidHoldingId, requestBody),
            startRpcFlow(davidHoldingId, requestBody)
        )

        flowIds.forEach {
            val flowResult = awaitRpcFlowFinished(davidHoldingId, it)
            assertThat(flowResult.flowError).isNull()
            assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        }
    }

    @Test
    fun `start RPC flow twice, second returns an error code of 409`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        startRpcFlow(bobHoldingId, requestBody)
        startRpcFlow(bobHoldingId, requestBody, 409)
    }

    @Test
    fun `start RPC flow for flow not in startable list, returns an error code of 400`() {
        startRpcFlow(bobHoldingId, emptyMap(), "InvalidFlow", 400)
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

    @Test
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
        assertThat(flowResult.result).startsWith("Type='PLATFORM_ERROR'")
    }

    @Test
    fun `error is thrown when flow with invalid constructor is executed`() {
        invalidConstructorFlowNames.forEach {
            val requestID = startRpcFlow(bobHoldingId, mapOf(), it)
            val result = awaitRpcFlowFinished(bobHoldingId, requestID)

            assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
            assertThat(result.flowError).isNotNull
            assertThat(result.flowError!!.message).contains(it)
        }
    }

    @Test
    fun `Persistence - persist a single entity`() {
        val id = UUID.randomUUID()
        val result = persistDog(id)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' saved")
    }

    @Test
    fun `Persistence - persist multiple entities`() {
        val id = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_persist_bulk"
            data = mapOf("ids" to "$id;$id2")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dogs ${listOf(id, id2)} saved")
    }

    @Test
    fun `Persistence - merge a single entity`() {
        val id = UUID.randomUUID()
        persistDog(id)
        val result = mergeDog(id, "dog2")
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' merged")
    }

    @Test
    fun `Persistence - merge multiple entities`() {
        val id = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        persistDog(id)
        persistDog(id2)

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_merge_bulk"
            data = mapOf(
                "ids" to "$id;$id2",
                "name" to "dog2",
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dogs ${listOf(id, id2)} merged")
    }

    @Test
    fun `Persistence - find a single entity`() {
        val id = UUID.randomUUID()
        val name = "new name"
        persistDog(id)
        mergeDog(id, name)

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_find"
            data = mapOf(
                "id" to id.toString()
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("found dog id='$id' name='$name")
    }

    @Test
    fun `Persistence - find multiple entities`() {
        val id = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val name = "new name"
        persistDog(id)
        persistDog(id2)
        mergeDog(id, name)
        mergeDog(id2, name)

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_find_bulk"
            data = mapOf("ids" to "$id;$id2")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).contains("id='$id' name='$name")
        assertThat(flowResult.result).contains("id='$id2' name='$name")
    }

    @Test
    fun `Persistence - find all entities`() {
        persistDog(UUID.randomUUID())

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_findall"
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("found one or more dogs")
    }

    @Test
    fun `Persistence - delete a single entity`() {
        val id = UUID.randomUUID()

        persistDog(id)

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_delete"
            data = mapOf(
                "id" to id.toString()
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dog '${id}' deleted")
    }

    @Test
    fun `Persistence - delete multiple entities`() {
        val id = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        persistDog(id)
        persistDog(id2)

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_delete_bulk"
            data = mapOf("ids" to "$id;$id2")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        val flowResult = result.getRpcFlowResult()
        assertThat(flowResult.result).isEqualTo("dogs ${listOf(id, id2)} deleted")
    }

    private fun persistDog(id: UUID): FlowStatus {
        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_persist"
            data = mapOf("id" to id.toString())
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        return result
    }

    private fun mergeDog(id: UUID, name: String): FlowStatus {
        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_merge"
            data = mapOf(
                "id" to id.toString(),
                "name" to name,
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        return result
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
    fun `Flow Session - Initiate multiple sessions and exercise the flow messaging apis`() {

        val requestBody = RpcSmokeTestInput().apply {
            command = "flow_messaging_apis"
            data = mapOf("sessions" to X500_BOB)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("flow_messaging_apis")
        assertThat(flowResult.result)
            .isEqualTo("${X500_BOB}=Completed. Sum:18")
    }

    @Test
    fun `Crypto - Sign and verify bytes`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_sign_and_verify"
            data = mapOf("memberX500" to X500_BOB)
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
        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_verify_invalid_signature"
            data = mapOf("memberX500" to X500_BOB)
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

    @Test
    fun `Context is propagated to initiated and sub flows`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "context_propagation"
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

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
                "user2": "null",
                "user3": "null"
              },
              "rpcSubFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "user2-set",
                "user3": "null"
              },
              "initiatedFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "user2-set",
                "user3": "user3-set"
              },
              "initiatedSubFlow": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "user2-set-ContextPropagationInitiatedFlow",
                "user3": "user3-set"
              },
              "rpcFlowAtComplete": {
                "platform": "account-zero",
                "user1": "user1-set",
                "user2": "null",
                "user3": "null"
              }
            }
            """.filter { !it.isWhitespace() }

        assertThat(flowResult.result)
            .isEqualTo(CONTEXT_JSON)
    }

    @Test
    fun `flows can use inheritance and platform dependencies are correctly injected`() {
        dependencyInjectionFlowNames.forEach {
            val requestId = startRpcFlow(bobHoldingId, mapOf("id" to X500_CHARLIE), it)
            val result = awaitRpcFlowFinished(bobHoldingId, requestId)

            assertThat(result.flowError).isNull()
            assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            assertThat(result.flowResult).isEqualTo(X500_CHARLIE)
        }
    }

    @Test
    fun `Serialize and deserialize an object`() {
        val dataToSerialize = "serialize this"
        val requestBody = RpcSmokeTestInput().apply {
            command = "serialization"
            data = mapOf("data" to dataToSerialize)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowError).isNull()
        assertThat(flowResult.result).isEqualTo(dataToSerialize)
        assertThat(flowResult.command).isEqualTo("serialization")
    }

    @Test
    fun `Uniqueness client service flow is finishing without exceptions`() {
        val requestID =
            startRpcFlow(
                bobHoldingId,
                mapOf(),
                "net.cordapp.testing.testflows.UniquenessCheckTestFlow"
            )
        val result = awaitRpcFlowFinished(bobHoldingId, requestID)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
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
                        data = mapOf("memberX500" to X500_BOB)
                    }
                ),

                startRpcFlow(
                    bobHoldingId,
                    RpcSmokeTestInput().apply {
                        command = "lookup_member_by_x500_name"
                        data = mapOf("id" to X500_CHARLIE)
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