package net.corda.applications.workers.smoketest.websocket

import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.e2etest.utilities.websocket.client.useWebsocketConnection
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_ALIAS
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_USAGE
import net.corda.e2etest.utilities.RpcSmokeTestInput
import net.corda.e2etest.utilities.assertWithRetryIgnoringExceptions
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.util.UUID

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FlowStatusFeedSmokeTest {

    private companion object {
        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        private val staticMemberList = listOf(
            bobX500,
        )

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            // Certificate upload can be slow in the combined worker, especially after it has just started up.
            cluster {
                assertWithRetryIgnoringExceptions {
                    timeout(Duration.ofSeconds(100))
                    interval(Duration.ofSeconds(1))
                    command { importCertificate(CODE_SIGNER_CERT, CODE_SIGNER_CERT_USAGE, CODE_SIGNER_CERT_ALIAS) }
                    condition { it.code == 204 }
                }
            }

            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(
                cpiName,
                TEST_CPB_LOCATION,
                groupId,
                staticMemberList
            )

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        }
    }

    private enum class FlowStates { START_REQUESTED, RUNNING, RETRYING, COMPLETED, FAILED, KILLED }

    private fun generateRequestId(identifyingTest: String): String {
        return "$identifyingTest-${UUID.randomUUID()}"
    }

    @Order(20)
    @Test
    fun `flow status update feed receives updates for the basic lifecycle of a flow`() {
        val clientRequestId = generateRequestId("test20")
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        useWebsocketConnection(flowStatusFeedPath) { wsHandler ->
            startFlow(clientRequestId)

            eventually(Duration.ofSeconds(300)) {
                assertThat(wsHandler.messageQueueSnapshot).hasSize(3)
            }
            val messageQueue = wsHandler.messageQueueSnapshot
            assertThat(messageQueue[0]).contains(FlowStates.START_REQUESTED.name)
            assertThat(messageQueue[1]).contains(FlowStates.RUNNING.name)
            assertThat(messageQueue[2]).contains(FlowStates.COMPLETED.name)
        }
    }

    private fun startFlow(clientRequestId: String) {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        startRpcFlow(holdingId = bobHoldingId, args = requestBody, requestId = clientRequestId)
    }
}
