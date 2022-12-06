package net.corda.applications.workers.smoketest.flow

import java.util.UUID
import kotlin.text.Typography.quote
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.applications.workers.smoketest.FlowStatus
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_FAILED
import net.corda.applications.workers.smoketest.RPC_FLOW_STATUS_SUCCESS
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.TEST_NOTARY_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_NOTARY_CPI_NAME
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
import net.corda.applications.workers.smoketest.toJsonString
import net.corda.applications.workers.smoketest.updateConfig
import net.corda.applications.workers.smoketest.waitForConfigurationChange
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.v5.crypto.DigestAlgorithmName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.TestMethodOrder

@Suppress("Unused", "FunctionName")
//The flow tests must go last as one test updates the messaging config which is highly disruptive to subsequent test runs. The real
// solution to this is a larger effort to have components listen to their messaging pattern lifecycle status and for them to go DOWN when
// their patterns are DOWN - CORE-8015
@Order(999)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests {

    companion object {
        private val testRunUniqueId = UUID.randomUUID()
        private val applicationCpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, GROUP_ID)
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
        private val davidX500 = "CN=David-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var davidHoldingId: String = getHoldingIdShortHash(davidX500, GROUP_ID)
        private val charlyX500 = "CN=Charley-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var charlieHoldingId: String = getHoldingIdShortHash(charlyX500, GROUP_ID)
        private val notaryX500 = "CN=Notary-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, GROUP_ID)
        private val staticMemberList = listOf(
            aliceX500,
            bobX500,
            charlyX500,
            davidX500,
            notaryX500
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
            "net.cordapp.testing.testflows.NotarisationTestFlow",
            "net.cordapp.testing.testflows.UniquenessCheckTestFlow"
        ) + invalidConstructorFlowNames + dependencyInjectionFlowNames

        val jacksonObjectMapper = jacksonObjectMapper()

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(
                applicationCpiName, TEST_CPB_LOCATION, GROUP_ID, staticMemberList)
            // Upload notary server CPB
            conditionallyUploadCordaPackage(
                notaryCpiName, TEST_NOTARY_CPB_LOCATION, GROUP_ID, staticMemberList)

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, applicationCpiName)
            val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlyX500, applicationCpiName)
            val davidActualHoldingId = getOrCreateVirtualNodeFor(davidX500, applicationCpiName)
            val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

            // Just validate the function and actual vnode holding ID hash are in sync
            // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
            assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)
            assertThat(davidActualHoldingId).isEqualTo(davidHoldingId)
            assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

            registerMember(bobHoldingId)
            registerMember(charlieHoldingId)
            registerMember(notaryHoldingId, isNotary = true)
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
                "sessions" to "${bobX500};${charlyX500}",
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
            .isEqualTo("${bobX500}=echo:m1; ${charlyX500}=echo:m2")
    }

    @Test
    fun `Platform Error - user code receives platform errors`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "throw_platform_error"
            data = mapOf("x500" to bobX500)
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
                "sessions" to "${bobX500};${charlyX500}",
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
            .isEqualTo("${bobX500}=echo:m1; ${charlyX500}=echo:m2")
    }

    @Test
    fun `SubFlow - Create an uninitiated session in an initiating flow and pass it to a inline subflow`() {

        val requestBody = RpcSmokeTestInput().apply {
            command = "subflow_passed_in_non_initiated_session"
            data = mapOf(
                "sessions" to "${bobX500};${charlyX500}",
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
            .isEqualTo("${bobX500}=echo:m1; ${charlyX500}=echo:m2")
    }

    @Test
    fun `Flow Session - Initiate multiple sessions and exercise the flow messaging apis`() {

        val requestBody = RpcSmokeTestInput().apply {
            command = "flow_messaging_apis"
            data = mapOf("sessions" to bobX500)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("flow_messaging_apis")
        assertThat(flowResult.result)
            .isEqualTo("${bobX500}=Completed. Sum:18")
    }

    @Test
    fun `Crypto - Sign and verify bytes`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_sign_and_verify"
            data = mapOf("memberX500" to bobX500)
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
            data = mapOf("memberX500" to bobX500)
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
    fun `Crypto - Get default signature spec`() {
        // Call get default signature spec api with public key and digest algorithm name
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_get_default_signature_spec"
        requestBody.data = mapOf(
            "memberX500" to bobX500,
            "digestName" to DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name
        )

        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val result = awaitRpcFlowFinished(bobHoldingId, requestId)
        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("crypto_get_default_signature_spec")
        assertThat(flowResult.result).isEqualTo("SHA256withECDSA")

        // Call get default signature spec api with public key only
        requestBody.data = mapOf(
            "memberX500" to bobX500
        )
        val requestId1 = startRpcFlow(bobHoldingId, requestBody)
        val result1 = awaitRpcFlowFinished(bobHoldingId, requestId1)
        val flowResult1 = result1.getRpcFlowResult()
        assertThat(result1.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result1.flowResult).isNotNull
        assertThat(result1.flowError).isNull()
        assertThat(flowResult1.command).isEqualTo("crypto_get_default_signature_spec")
        assertThat(flowResult1.result).isEqualTo("SHA256withECDSA")
    }

    @Test
    fun `Crypto - Get compatible signature specs`() {
        // Call get compatible signature specs api with public key only
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_get_compatible_signature_specs"
        requestBody.data = mapOf("memberX500" to bobX500)

        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val result = awaitRpcFlowFinished(bobHoldingId, requestId)
        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("crypto_get_compatible_signature_specs")
        val flowOutputs = requireNotNull(flowResult.result).split("; ")
        assertThat(flowOutputs).containsAll(
            listOf(
                "SHA256withECDSA",
                "SHA384withECDSA",
                "SHA512withECDSA"
            )
        )

        // Call get compatible signature specs api with public key and digest algorithm name
        requestBody.data = mapOf(
            "memberX500" to bobX500,
            "digestName" to DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name
        )

        val requestId1 = startRpcFlow(bobHoldingId, requestBody)
        val result1 = awaitRpcFlowFinished(bobHoldingId, requestId1)
        val flowResult1 = result1.getRpcFlowResult()
        assertThat(result1.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result1.flowResult).isNotNull
        assertThat(result1.flowError).isNull()
        assertThat(flowResult1.command).isEqualTo("crypto_get_compatible_signature_specs")
        val flowOutputs1 = requireNotNull(flowResult1.result).split("; ")
        assertThat(flowOutputs1).containsAll(listOf("SHA256withECDSA"))
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

        val contextJson =
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
            """.trimJson()

        assertThat(flowResult.result).isEqualTo(contextJson)
    }

    @Test
    fun `flows can use inheritance and platform dependencies are correctly injected`() {
        dependencyInjectionFlowNames.forEach {
            val requestId = startRpcFlow(bobHoldingId, mapOf("id" to charlyX500), it)
            val result = awaitRpcFlowFinished(bobHoldingId, requestId)

            assertThat(result.flowError).isNull()
            assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            assertThat(result.flowResult).isEqualTo(charlyX500)
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
    fun `Notary - Uniqueness client service flow is finishing without exceptions`() {
        val requestID = startRpcFlow(
            bobHoldingId,
            mapOf(),
            "net.cordapp.testing.testflows.UniquenessCheckTestFlow"
        )
        val result = awaitRpcFlowFinished(bobHoldingId, requestID)
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
    }

    @Test
    fun `Notary - Non-validating plugin is loaded and executes successfully when using issuance transaction`() {
        issueStatesAndValidateResult(3) { issuanceResult ->
            // 1. Make sure the states were issued
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

            val flowResultMap = issuanceResult.mapFlowJsonResult()

            assertAll({
                assertThat((flowResultMap["issuedStateRefs"] as List<*>)).hasSize(3)

                // 2. Make sure no extra states were consumed
                assertThat(flowResultMap["consumedInputStateRefs"] as List<*>).hasSize(0)
                assertThat(flowResultMap["consumedReferenceStateRefs"] as List<*>).hasSize(0)
            })
        }
    }

    @Test
    fun `Notary - Non-validating plugin returns error when time window invalid`() {
        issueStatesAndValidateResult(
            3,
            timeWindowLowerBoundOffsetMs = -2000,
            timeWindowUpperBoundOffsetMs = -1000
        ) { issuanceResult ->
            assertAll({
                assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                assertThat(issuanceResult.flowError?.message).contains("Unable to notarise transaction")
                assertThat(issuanceResult.flowError?.message).contains("NotaryErrorTimeWindowOutOfBounds")
            })
        }
    }

    @Test
    @Disabled
    // TODO CORE-7939 For now it's impossible to test this scenario as there's no back-chain resolution
    fun `Notary - Non-validating plugin executes successfully and returns signatures when consuming a valid transaction`() {
        // 1. Issue 1 state
        val issuedStates = mutableListOf<String>()
        issueStatesAndValidateResult(1) { issuanceResult ->
            // 2. Make sure the states were issued
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            val flowResultMap = issuanceResult.mapFlowJsonResult()

            @Suppress("unchecked_cast")
            val issuedStateRefs = flowResultMap["issuedStateRefs"] as List<String>

            assertThat(issuedStateRefs).hasSize(1)

            issuedStates.addAll(issuedStateRefs)

            // 3. Make sure no states were consumed
            assertAll({
                assertThat(flowResultMap["consumedInputStateRefs"] as List<*>).hasSize(0)
                assertThat(flowResultMap["consumedReferenceStateRefs"] as List<*>).hasSize(0)
            })
        }

        // 4. Consume one of the issued states as an input state
        consumeStatesAndValidateResult(
            inputStates = listOf(issuedStates.first()),
            refStates = emptyList()
        ) { consumeResult ->
            assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

            // 5. Make sure only one input state was consumed, and nothing was issued
            val flowResultMap = consumeResult.mapFlowJsonResult()

            assertAll({
                // Make sure we consumed the state we issued before
                @Suppress("unchecked_cast")
                val consumedInputs = flowResultMap["consumedInputStateRefs"] as List<String>

                assertThat(consumedInputs).hasSize(1)
                assertThat(consumedInputs.first()).isEqualTo(issuedStates.first())

                assertThat(flowResultMap["consumedReferenceStateRefs"] as List<*>).hasSize(0)
                assertThat(flowResultMap["issuedStateRefs"] as List<*>).hasSize(0)
            })
        }
    }

    @Test
    @Disabled
    // TODO CORE-7939 For now it's impossible to test this scenario as there's no back-chain resolution
    fun `Notary - Non-validating plugin returns error when using reference state that is spent in same tx`() {
        // 1. Issue 1 state
        val issuedStates = mutableListOf<String>()
        issueStatesAndValidateResult(1) { issuanceResult ->
            // 2. Make sure the states were issued
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            val flowResultMap = issuanceResult.mapFlowJsonResult()

            @Suppress("unchecked_cast")
            val issuedStateRefs = flowResultMap["issuedStateRefs"] as List<String>

            assertThat(issuedStateRefs).hasSize(1)

            issuedStates.addAll(issuedStateRefs)

            // 3. Make sure no states were consumed
            assertAll({
                assertThat(flowResultMap["consumedInputStateRefs"] as List<*>).hasSize(0)
                assertThat(flowResultMap["consumedReferenceStateRefs"] as List<*>).hasSize(0)
            })
        }

        // 4. Include one of the issued states twice in the same TX (as input and as ref)
        val toConsume = issuedStates.first()

        consumeStatesAndValidateResult(
            inputStates = listOf(toConsume),
            refStates = listOf(toConsume)
        ) { consumeResult ->
            // 5. Make sure the request failed due to double spend error
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                assertThat(consumeResult.flowError?.message).contains("Unable to notarise transaction")
                assertThat(consumeResult.flowError?.message).contains("NotaryErrorReferenceStateConflict")
            })
        }
    }

    @Test
    @Disabled
    // TODO CORE-7939 For now it's impossible to test this scenario as there's no back-chain resolution
    fun `Notary - Non-validating plugin returns error when trying to spend unknown input state`() {
        consumeStatesAndValidateResult(
            inputStates = listOf(
                "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903:0"
            ),
            refStates = emptyList()
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                assertThat(consumeResult.flowError?.message).contains("Unable to notarise transaction")
                assertThat(consumeResult.flowError?.message).contains("NotaryErrorInputStateUnknown")
            })
        }
    }

    @Test
    @Disabled
    // TODO CORE-7939 For now it's impossible to test this scenario as there's no back-chain resolution.
    fun `Notary - Non-validating plugin returns error when trying to spend unknown reference state`() {
        consumeStatesAndValidateResult(
            inputStates = emptyList(),
            refStates = listOf(
                "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903:0"
            )
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                assertThat(consumeResult.flowError?.message).contains("Unable to notarise transaction")
                assertThat(consumeResult.flowError?.message).contains("NotaryErrorInputStateUnknown")
            })
        }
    }

    @Test
    fun `Notary - Plugin that is not present on the network cannot be loaded`() {
        issueStatesAndValidateResult(1, pluginType = "non-existing-plugin") { issuanceResult ->
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
            assertThat(issuanceResult.flowError?.message)
                .contains("Notary flow provider not found for type: non-existing-plugin")
        }
    }

    @Test
    fun `Notary - Plugin that cannot be instantiated will throw exception`() {
        issueStatesAndValidateResult(1, pluginType = "invalid-notary-plugin") { issuanceResult ->
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
            assertThat(issuanceResult.flowError?.message)
                .contains("Invalid plugin, cannot be loaded!")
        }
    }

    @Test
    fun `Notary - Valid plugin can be loaded and will be executed`() {
        issueStatesAndValidateResult(1, pluginType = "valid-notary-plugin") { issuanceResult ->
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
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

    @Test
    fun `Json serialisation`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "json_serialization"
            data = mapOf("vnode" to bobX500)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRpcFlowFinished(bobHoldingId, requestId)

        val flowResult = result.getRpcFlowResult()
        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(result.flowResult).isNotNull
        assertThat(result.flowError).isNull()
        assertThat(flowResult.command).isEqualTo("json_serialization")

        val expectedOutputJson =
            """
            {
              "firstTest": {
                "serialized-implicitly": "combined-test-stringtest-string"
              },
              "secondTest": "$bobX500"
            }
            """.trimJson()

        assertThat(flowResult.result).isEqualTo(expectedOutputJson)
    }

    /**
     * Generates an issuance transaction with the given amount of output states, runs it through the notarisation flow,
     * then runs the given [validateResult] block on the flow result.
     */
    private fun issueStatesAndValidateResult(
        outputStateCount: Int,
        timeWindowLowerBoundOffsetMs: Long? = null,
        timeWindowUpperBoundOffsetMs: Long? = null,
        pluginType: String? = null,
        validateResult: (flowResult: FlowStatus) -> Unit
    ) {
        val paramMap = mutableMapOf("outputStateCount" to "$outputStateCount")
        timeWindowLowerBoundOffsetMs?.let {
            paramMap.put("timeWindowLowerBoundOffsetMs", "$it")
        }
        timeWindowUpperBoundOffsetMs?.let {
            paramMap.put("timeWindowUpperBoundOffsetMs", "$it")
        }
        pluginType?.let {
            paramMap.put("pluginType", it)
        }

        val issuanceRequestID = startRpcFlow(
            bobHoldingId,
            paramMap,
            "net.cordapp.testing.testflows.NotarisationTestFlow"
        )

        val issuanceResult = awaitRpcFlowFinished(bobHoldingId, issuanceRequestID)

        validateResult(issuanceResult)
    }

    /**
     * Consumes the provided states as either input or ref states, and runs it through the notarisation flow,
     * then runs the given [validateResult] block on the flow result.
     */
    private fun consumeStatesAndValidateResult(
        inputStates: List<String>,
        refStates: List<String>,
        validateResult: (flowResult: FlowStatus) -> Unit
    ) {
        val consumeRequestID = startRpcFlow(
            bobHoldingId,
            mapOf(
                "inputStateRefs" to inputStates,
                "referenceStateRefs" to refStates
            ),
            "net.cordapp.testing.testflows.NotarisationTestFlow"
        )

        val consumeResult = awaitRpcFlowFinished(bobHoldingId, consumeRequestID)

        validateResult(consumeResult)
    }

    private fun FlowStatus.mapFlowJsonResult() = jacksonObjectMapper.readValue<Map<String, Any>>(this.flowResult!!)
}
