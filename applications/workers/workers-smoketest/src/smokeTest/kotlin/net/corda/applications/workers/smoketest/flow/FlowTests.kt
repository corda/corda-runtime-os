package net.corda.applications.workers.smoketest.flow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.FlowResult
import net.corda.e2etest.utilities.REST_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.RestSmokeTestInput
import net.corda.e2etest.utilities.TEST_NOTARY_CPB_LOCATION
import net.corda.e2etest.utilities.TEST_NOTARY_CPI_NAME
import net.corda.e2etest.utilities.TestRequestIdGenerator
import net.corda.e2etest.utilities.awaitRestFlowResult
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRestFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import kotlin.text.Typography.quote

@Suppress("Unused", "FunctionName")
@TestInstance(Lifecycle.PER_CLASS)
class FlowTests : ClusterReadiness by ClusterReadinessChecker() {

    companion object {
        private const val testRunUniqueId = "5a90563f-73a0-46ce-a7e4-28354b6c686c"
        private const val groupId = "d1f30558-4627-495c-8ea5-2fb4b9273c74"
        private const val applicationCpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private const val notaryCpiName = "${TEST_NOTARY_CPI_NAME}_$testRunUniqueId"
        private const val bobX500 = "CN=Bob-5a90563f-73a0-46ce-a7e4-28354b6c686c, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        private const val notaryX500 = "CN=Notary-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val notaryHoldingId: String = getHoldingIdShortHash(notaryX500, groupId)
        private val staticMemberList = listOf(
            bobX500,
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
            "com.r3.corda.testing.smoketests.flow.RestSmokeTestFlow",
            "com.r3.corda.testing.testflows.TestFlow",
            "com.r3.corda.testing.testflows.BrokenProtocolFlow",
            "com.r3.corda.testing.testflows.MessagingFlow",
            "com.r3.corda.testing.testflows.FlowSessionTimeoutFlow",
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
//        assertIsReady(Duration.ofMinutes(2), Duration.ofMillis(100))

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
        val notaryActualHoldingId = getOrCreateVirtualNodeFor(notaryX500, notaryCpiName)

        // Just validate the function and actual vnode holding ID hash are in sync
        // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(notaryActualHoldingId).isEqualTo(notaryHoldingId)

        registerStaticMember(bobHoldingId)
        registerStaticMember(notaryHoldingId, NOTARY_SERVICE_X500)
    }

    @Test
    fun `Crypto - Sign and verify bytes`(testInfo: TestInfo) {
        val idGenerator = TestRequestIdGenerator(testInfo)
        val requestBody = RestSmokeTestInput().apply {
            command = "crypto_sign_and_verify"
            data = mapOf("memberX500" to bobX500)
        }

        val requestId = startRestFlow(bobHoldingId, requestBody, requestId = idGenerator.nextId)

        val flowResult = awaitRestFlowResult(bobHoldingId, requestId)

        assertThat(flowResult.flowStatus).isEqualTo(REST_FLOW_STATUS_SUCCESS)
        assertThat(flowResult.json).isNotNull
        assertThat(flowResult.flowError).isNull()
        assertThat(flowResult.json.command).isEqualTo("crypto_sign_and_verify")
        assertThat(flowResult.json.result).isEqualTo(true.toString())
    }
}
