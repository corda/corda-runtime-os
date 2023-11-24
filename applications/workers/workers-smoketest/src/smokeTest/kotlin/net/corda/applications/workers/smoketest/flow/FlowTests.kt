package net.corda.applications.workers.smoketest.flow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.FlowResult
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_FAILED
import net.corda.e2etest.utilities.RPC_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.RpcSmokeTestInput
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.awaitRestFlowResult
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.v5.crypto.DigestAlgorithmName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.time.Duration
import java.util.UUID
import kotlin.text.Typography.quote

@Suppress("Unused", "FunctionName")
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests : ClusterReadiness by ClusterReadinessChecker() {

    companion object {
        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val applicationCpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        private val davidX500 = "CN=David-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var davidHoldingId: String = getHoldingIdShortHash(davidX500, groupId)
        private val charlyX500 = "CN=Charley-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var charlieHoldingId: String = getHoldingIdShortHash(charlyX500, groupId)
        private val notaryX500 = "CN=Notary-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, groupId)
        private val staticMemberList = listOf(
            aliceX500,
            bobX500,
            charlyX500,
            davidX500,
            notaryX500
        )
        private const val NOTARY_SERVICE_X500 = "O=MyNotaryService, L=London, C=GB"

        val invalidConstructorFlowNames = listOf(
            "com.r3.corda.testing.smoketests.flow.errors.PrivateConstructorFlow",
            "com.r3.corda.testing.smoketests.flow.errors.PrivateConstructorJavaFlow",
            "com.r3.corda.testing.smoketests.flow.errors.NoDefaultConstructorFlow",
            "com.r3.corda.testing.smoketests.flow.errors.NoDefaultConstructorJavaFlow",
        )

        val dependencyInjectionFlowNames = listOf(
            "com.r3.corda.testing.smoketests.flow.DependencyInjectionTestFlow",
            "com.r3.corda.testing.smoketests.flow.inheritance.DependencyInjectionTestJavaFlow",
        )

        val expectedFlows = listOf(
            "com.r3.corda.testing.smoketests.virtualnode.ReturnAStringFlow",
            "com.r3.corda.testing.smoketests.virtualnode.SimplePersistenceCheckFlow",
            "com.r3.corda.testing.smoketests.flow.AmqpSerializationTestFlow",
            "com.r3.corda.testing.smoketests.flow.RpcSmokeTestFlow",
            "com.r3.corda.testing.testflows.TestFlow",
            "com.r3.corda.testing.testflows.BrokenProtocolFlow",
            "com.r3.corda.testing.testflows.MessagingFlow",
            "com.r3.corda.testing.testflows.PersistenceFlow",
            "com.r3.corda.testing.testflows.NonValidatingNotaryTestFlow",
            "com.r3.corda.testing.testflows.ledger.TokenSelectionFlow"
        ) + invalidConstructorFlowNames + dependencyInjectionFlowNames

        val jacksonObjectMapper = jacksonObjectMapper()

        private val JsonNode?.command: String
            get() {
                return this!!["command"].textValue()
            }

        private val JsonNode?.result: String
            get() {
                return this!!["result"].textValue()
            }

        private fun FlowResult.mapFlowJsonResult(): Map<*, *> =
            json!!.traverse(jacksonObjectMapper).readValueAs(Map::class.java)
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

    @BeforeAll
    internal fun beforeAll() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))

        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()

        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(
            applicationCpiName, TEST_CPB_LOCATION, groupId, staticMemberList
        )
        // Upload notary server CPB
        conditionallyUploadCordaPackage(
            notaryCpiName,
            TEST_NOTARY_CPB_LOCATION,
            groupId,
            staticMemberList
        )

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

        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)
        registerStaticMember(notaryHoldingId, NOTARY_SERVICE_X500)
    }

    @Test
    fun `start RPC flow`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("echo")
        assertThat(flowResult.json.result).isEqualTo("hello")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("start_sessions")
        assertThat(flowResult.json.result)
            .isEqualTo("${bobX500}=echo:m1; ${charlyX500}=echo:m2")
    }

    @Test
    fun `Persistence - persist a single entity`() {
        val id = UUID.randomUUID()
        val flowResult = persistDog(id)
        assertThat(flowResult.json.result).isEqualTo("dog '${id}' saved")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json.result).isEqualTo("dogs ${listOf(id, id2)} saved")
    }

    @Test
    fun `Persistence - merge a single entity`() {
        val id = UUID.randomUUID()
        persistDog(id)
        val flowResult = mergeDog(id, "dog2")
        assertThat(flowResult.json.result).isEqualTo("dog '${id}' merged")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        assertThat(flowResult.json.result).isEqualTo("dogs ${listOf(id, id2)} merged")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json.result).isEqualTo("found dog id='$id' name='$name'")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json.result).contains("id='$id' name='$name'")
        assertThat(flowResult.json.result).contains("id='$id2' name='$name'")
    }

    @Test
    fun `Persistence - find all entities`() {
        persistDog(UUID.randomUUID())

        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_findall"
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json.result).isEqualTo("found one or more dogs")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json.result).isEqualTo("dog '${id}' deleted")
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

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json.result).isEqualTo("dogs ${listOf(id, id2)} deleted")
    }

    @Test
    fun `CPI metadata is available in a flow`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "get_cpi_metadata"
            data = emptyMap()
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId).mapFlowJsonResult()
        val result = jacksonObjectMapper.readValue<Map<String, Any>>(flowResult["result"] as String)

        assertThat(result["cpiName"] as String).isEqualTo(applicationCpiName)
        assertThat(result["cpiVersion"] as String).isNotNull.isNotEmpty
        assertThat(result["cpiFileChecksum"] as String).isNotNull.isNotEmpty
        assertThat(result["cpiSignerSummaryHash"] as String).isNotNull.isNotEmpty
        assertThat(result["initialPlatformVersion"] as String).isNotNull.isNotEmpty
        assertThat(result["initialSoftwareVersion"] as String).isNotNull.isNotEmpty
    }

    private fun persistDog(id: UUID): FlowResult {
        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_persist"
            data = mapOf("id" to id.toString())
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        return result
    }

    private fun mergeDog(id: UUID, name: String): FlowResult {
        val requestBody = RpcSmokeTestInput().apply {
            command = "persistence_merge"
            data = mapOf(
                "id" to id.toString(),
                "name" to name,
            )
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val result = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)

        return result
    }

    @Test
    fun `Crypto - Sign and verify bytes`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_sign_and_verify"
            data = mapOf("memberX500" to bobX500)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("crypto_sign_and_verify")
        assertThat(flowResult.json.result).isEqualTo(true.toString())
    }

    @Test
    fun `Crypto - Verify invalid signature`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "crypto_verify_invalid_signature"
            data = mapOf("memberX500" to bobX500)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("crypto_verify_invalid_signature")
        assertThat(flowResult.json.result).isEqualTo(true.toString())
    }

    @Test
    fun `Crypto - Get default signature spec`() {
        // Call get default signature spec api with public key and digest algorithm name
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_get_default_signature_spec"
        requestBody.data = mapOf(
            "memberX500" to bobX500,
            "digestName" to DigestAlgorithmName.SHA2_256.name
        )

        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("crypto_get_default_signature_spec")
        assertThat(flowResult.json.result).isEqualTo("SHA256withECDSA")

        // Call get default signature spec api with public key only
        requestBody.data = mapOf(
            "memberX500" to bobX500
        )
        val requestId1 = startRpcFlow(bobHoldingId, requestBody)
        val flowResult1 = awaitRestFlowResult(bobHoldingId, requestId1)
        assertThat(flowResult1.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult1.json).isNotNull
        assertThat(flowResult1.flowError).isNull()
        assertThat(flowResult1.json.command).isEqualTo("crypto_get_default_signature_spec")
        assertThat(flowResult1.json.result).isEqualTo("SHA256withECDSA")
    }

    @Test
    fun `Crypto - Get compatible signature specs`() {
        // Call get compatible signature specs api with public key only
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_get_compatible_signature_specs"
        requestBody.data = mapOf("memberX500" to bobX500)

        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("crypto_get_compatible_signature_specs")
        val flowOutputs = requireNotNull(flowResult.json.result).split("; ")
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
            "digestName" to DigestAlgorithmName.SHA2_256.name
        )

        val requestId1 = startRpcFlow(bobHoldingId, requestBody)
        val flowResult1 = awaitRestFlowResult(bobHoldingId, requestId1)
        assertThat(flowResult1.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult1.json).isNotNull
        assertThat(flowResult1.flowError).isNull()
        assertThat(flowResult1.json.command).isEqualTo("crypto_get_compatible_signature_specs")
        val flowOutputs1 = requireNotNull(flowResult1.json.result).split("; ")
        assertThat(flowOutputs1).containsAll(listOf("SHA256withECDSA"))
    }

    @Test
    fun `Crypto - Signing service finds my signing keys`() {
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_find_my_signing_keys"
        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.json.command).isEqualTo("crypto_find_my_signing_keys")
        assertThat(flowResult.json.result).isEqualTo("success")
    }

    @Test
    fun `Crypto - CompositeKeyGenerator works in flows`() {
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_CompositeKeyGenerator_works_in_flows"
        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.json.command).isEqualTo("crypto_CompositeKeyGenerator_works_in_flows")
        assertThat(flowResult.json.result).isEqualTo("SUCCESS")
    }

    @Test
    fun `Crypto - Get default digest algorithm`() {
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_get_default_digest_algorithm"
        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.json.command).isEqualTo("crypto_get_default_digest_algorithm")
        assertThat(flowResult.json.result).isEqualTo("SUCCESS")
    }

    @Test
    fun `Crypto - Get supported digest algorithms`() {
        val requestBody = RpcSmokeTestInput()
        requestBody.command = "crypto_get_supported_digest_algorithms"
        val requestId = startRpcFlow(bobHoldingId, requestBody)
        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)
        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.json.command).isEqualTo("crypto_get_supported_digest_algorithms")
        assertThat(flowResult.json.result).isEqualTo("SUCCESS")
    }

    @Test
    fun `Notary - Non-validating plugin executes successfully when using issuance transaction`() {
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
                assertThat(issuanceResult.flowError?.message).contains("Unable to notarize transaction")
                assertThat(issuanceResult.flowError?.message).contains("Time Window Out of Bounds")
            })
        }
    }

    @Test
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
    fun `Notary - Non-validating plugin returns error on double spend`() {
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

        // 4. Spend the issued state
        val toConsume = issuedStates.first()

        consumeStatesAndValidateResult(
            inputStates = listOf(toConsume),
            refStates = emptyList()
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            })
        }

        // 5. Try to spend the state again, and expect the error
        consumeStatesAndValidateResult(
            inputStates = listOf(toConsume),
            refStates = emptyList()
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                assertThat(consumeResult.flowError?.message).contains("Unable to notarize transaction")
                assertThat(consumeResult.flowError?.message).contains("Input State Conflict")
            })
        }
    }

    @Test
    fun `Notary - Non-validating plugin returns error when trying to spend unknown reference state`() {
        // Random unknown StateRef
        val unknownStateRef = "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903:0"
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

        // 4. Spend a valid state and reference an unknown state
        consumeStatesAndValidateResult(
            inputStates = listOf(issuedStates.first()),
            refStates = listOf(
                unknownStateRef
            )
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                // This will fail when building the transaction BEFORE reaching the plugin logic so we don't
                // expect notarization error here
                assertThat(consumeResult.flowError?.message).contains(
                    "Could not find StateRef $unknownStateRef " +
                            "when resolving reference states."
                )
            })
        }
    }

    @Test
    fun `Notary - Non-validating plugin returns error when using the same state for input and ref`() {
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

        // 4. Make sure we consumed the state and managed to reference it
        // Since the state we are trying to spend and reference is not spent yet (not persisted) we should be able
        // to spend it and reference at the same time
        consumeStatesAndValidateResult(
            inputStates = listOf(issuedStates.first()),
            refStates = listOf(issuedStates.first())
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                // This will fail when building the transaction BEFORE reaching the plugin logic so
                // we don't expect notarization error here
                assertThat(consumeResult.flowError?.message).contains(
                    "A state cannot be both an input and a reference input in the same " +
                            "transaction. Offending states: $issuedStates"

                )
            })
        }
    }

    @Test
    fun `Notary - Non-validating plugin returns error when trying to spend unknown input state`() {
        // Random unknown StateRef
        val unknownStateRef = "SHA-256:CDFF8A944383063AB86AFE61488208CCCC84149911F85BE4F0CACCF399CA9903:0"
        consumeStatesAndValidateResult(
            inputStates = listOf(
                unknownStateRef
            ),
            refStates = emptyList()
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                // This will fail when building the transaction BEFORE reaching the plugin logic so we don't
                // expect notarization error here
                assertThat(consumeResult.flowError?.message).contains(
                    "Could not find StateRef $unknownStateRef " +
                            "when resolving input states."

                )
            })
        }
    }

    @Test
    fun `Notary - Non-validating plugin returns error when referencing spent state`() {
        // 1. Issue 2 states
        val issuedStates = mutableListOf<String>()
        issueStatesAndValidateResult(2) { issuanceResult ->
            // 2. Make sure the states were issued
            assertThat(issuanceResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            val flowResultMap = issuanceResult.mapFlowJsonResult()

            @Suppress("unchecked_cast")
            val issuedStateRefs = flowResultMap["issuedStateRefs"] as List<String>

            assertThat(issuedStateRefs).hasSize(2)

            issuedStates.addAll(issuedStateRefs)

            // 3. Make sure no states were consumed
            assertAll({
                assertThat(flowResultMap["consumedInputStateRefs"] as List<*>).hasSize(0)
                assertThat(flowResultMap["consumedReferenceStateRefs"] as List<*>).hasSize(0)
            })
        }

        // 4. Spend the issued state
        consumeStatesAndValidateResult(
            inputStates = listOf(issuedStates.first()),
            refStates = emptyList()
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
            })
        }

        // 5. Try to reference the spent state.
        // Note we MUST spend or issue a state otherwise it is not a valid transaction. In this case we choose to spend
        // it because it's easier to do with `consumeStatesAndValidateResult`.
        consumeStatesAndValidateResult(
            inputStates = listOf(issuedStates[1]),
            refStates = listOf(issuedStates.first())
        ) { consumeResult ->
            assertAll({
                assertThat(consumeResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_FAILED)
                assertThat(consumeResult.flowError?.message).contains("Unable to notarize transaction")
                assertThat(consumeResult.flowError?.message).contains("Reference State Conflict")
            })
        }
    }

    @Test
    fun `Json serialisation`() {
        val requestBody = RpcSmokeTestInput().apply {
            command = "json_serialization"
            data = mapOf("vnode" to bobX500)
        }

        val requestId = startRpcFlow(bobHoldingId, requestBody)

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("json_serialization")

        val expectedOutputJson =
            """
            {
              "firstTest": {
                "serialized-implicitly": "combined-test-stringtest-string"
              },
              "secondTest": "$bobX500"
            }
            """.trimJson()

        assertThat(flowResult.json.result).isEqualTo(expectedOutputJson)
    }

    /**
     * Generates an issuance transaction with the given amount of output states, runs it through the notarization flow,
     * then runs the given [validateResult] block on the flow result.
     */
    private fun issueStatesAndValidateResult(
        outputStateCount: Int,
        timeWindowLowerBoundOffsetMs: Long? = null,
        timeWindowUpperBoundOffsetMs: Long? = null,
        validateResult: (flowResult: FlowResult) -> Unit
    ) {
        val paramMap = mutableMapOf("outputStateCount" to "$outputStateCount")
        timeWindowLowerBoundOffsetMs?.let {
            paramMap.put("timeWindowLowerBoundOffsetMs", "$it")
        }
        timeWindowUpperBoundOffsetMs?.let {
            paramMap.put("timeWindowUpperBoundOffsetMs", "$it")
        }

        val issuanceRequestID = startRpcFlow(
            bobHoldingId,
            paramMap,
            "com.r3.corda.testing.testflows.NonValidatingNotaryTestFlow"
        )

        val issuanceResult = awaitRestFlowResult(bobHoldingId, issuanceRequestID)

        validateResult(issuanceResult)
    }

    /**
     * Consumes the provided states as either input or ref states, and runs it through the notarization flow,
     * then runs the given [validateResult] block on the flow result.
     */
    private fun consumeStatesAndValidateResult(
        inputStates: List<String>,
        refStates: List<String>,
        validateResult: (flowResult: FlowResult) -> Unit
    ) {
        val consumeRequestID = startRpcFlow(
            bobHoldingId,
            mapOf(
                "inputStateRefs" to jacksonObjectMapper.writeValueAsString(inputStates),
                "referenceStateRefs" to jacksonObjectMapper.writeValueAsString(refStates)
            ),
            "com.r3.corda.testing.testflows.NonValidatingNotaryTestFlow"
        )

        val consumeResult = awaitRestFlowResult(bobHoldingId, consumeRequestID)

        validateResult(consumeResult)
    }
}
