package net.corda.applications.workers.smoketest.websocket

import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.websocket.client.MessageQueueWebSocketHandler
import net.corda.applications.workers.smoketest.websocket.client.SmokeTestWebsocketClient
import net.corda.applications.workers.smoketest.websocket.client.useWebsocketConnection
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.GROUP_ID
import net.corda.e2etest.utilities.RpcSmokeTestInput
import net.corda.e2etest.utilities.SMOKE_TEST_CLASS_NAME
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.getFlowClasses
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
        private val staticMemberList = listOf(
            bobX500,
        )

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            // Certificate upload can be slow in the combined worker, especially after it has just started up.
            cluster {
                endpoint(
                    net.corda.e2etest.utilities.CLUSTER_URI,
                    net.corda.e2etest.utilities.USERNAME,
                    net.corda.e2etest.utilities.PASSWORD
                )
                assertWithRetry {
                    timeout(Duration.ofSeconds(100))
                    interval(Duration.ofSeconds(1))
                    command { importCertificate(CODE_SIGNER_CERT, "code-signer", "cordadev") }
                    condition { it.code == 204 }
                }
            }

            // Upload test flows if not already uploaded
            conditionallyUploadCordaPackage(
                cpiName,
                TEST_CPB_LOCATION,
                GROUP_ID,
                staticMemberList
            )

            // Make sure Virtual Nodes are created
            val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, cpiName)
            assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        }
    }

    private enum class FlowStates { START_REQUESTED, RUNNING, RETRYING, COMPLETED, FAILED }

    private fun generateRequestId(identifyingTest: String): String {
        return "$identifyingTest-${UUID.randomUUID()}"
    }

    @Order(10)
    @Test
    fun `websocket connection can be opened to listen for updates for flow clientRequestid`() {
        val flowStatusFeedPath = "/flow/$bobHoldingId/${generateRequestId("test10")}"

        val wsHandler = MessageQueueWebSocketHandler()

        val client = SmokeTestWebsocketClient()
        client.start()
        client.connect(flowStatusFeedPath, wsHandler)
        eventually {
            assertTrue(wsHandler.isConnected)
        }

        client.close()
        eventually {
            assertTrue(wsHandler.isNotConnected)
        }
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

    @Order(30)
    @Test
    fun `multiple websocket connections can be open for one flow from one holding identity and request id`() {
        val clientRequestId = generateRequestId("test30")
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        useWebsocketConnection(flowStatusFeedPath) { wsHandler1 ->
            useWebsocketConnection(flowStatusFeedPath) { wsHandler2 ->
                startFlow(clientRequestId)

                eventually(Duration.ofSeconds(300)) {
                    assertThat(wsHandler1.messageQueueSnapshot).hasSize(3)
                    assertThat(wsHandler2.messageQueueSnapshot).hasSize(3)
                }
                val messageQueue1 = wsHandler1.messageQueueSnapshot
                val messageQueue2 = wsHandler2.messageQueueSnapshot
                assertThat(messageQueue1[0]).contains(FlowStates.START_REQUESTED.name)
                assertThat(messageQueue1[1]).contains(FlowStates.RUNNING.name)
                assertThat(messageQueue1[2]).contains(FlowStates.COMPLETED.name)
                assertThat(messageQueue2[0]).contains(FlowStates.START_REQUESTED.name)
                assertThat(messageQueue2[1]).contains(FlowStates.RUNNING.name)
                assertThat(messageQueue2[2]).contains(FlowStates.COMPLETED.name)
            }
        }
    }

    @Order(31)
    @Test
    fun `multiple websocket connections can be open for two flows and receive the correct statuses`() {
        val clientRequestId1 = generateRequestId("test31-req1")
        val clientRequestId2 = generateRequestId("test31-req2")
        val flowStatusFeedPath1 = "/flow/$bobHoldingId/$clientRequestId1"
        val flowStatusFeedPath2 = "/flow/$bobHoldingId/$clientRequestId2"

        fun assertNormalFlowStatusesForRequest(messageQueue: List<String>, clientRequestId1: String) {
            assertThat(messageQueue[0]).contains(FlowStates.START_REQUESTED.name)
            assertThat(messageQueue[0]).contains(clientRequestId1)
            assertThat(messageQueue[1]).contains(FlowStates.RUNNING.name)
            assertThat(messageQueue[1]).contains(clientRequestId1)
            assertThat(messageQueue[2]).contains(FlowStates.COMPLETED.name)
            assertThat(messageQueue[2]).contains(clientRequestId1)
        }

        useWebsocketConnection(flowStatusFeedPath1) { wsHandler1 ->
            useWebsocketConnection(flowStatusFeedPath2) { wsHandler2 ->
                startFlow(clientRequestId1)
                startFlow(clientRequestId2)

                eventually(Duration.ofSeconds(300)) {
                    assertThat(wsHandler1.messageQueueSnapshot).hasSize(3)
                    assertThat(wsHandler2.messageQueueSnapshot).hasSize(3)
                }

                assertNormalFlowStatusesForRequest(wsHandler1.messageQueueSnapshot, clientRequestId1)
                assertNormalFlowStatusesForRequest(wsHandler2.messageQueueSnapshot, clientRequestId2)
            }
        }
    }

    @Order(40)
    @Test
    fun `registering for flow status feed when flow is already finished sends the finished status and terminates connection`() {
        val clientRequestId = generateRequestId("test40")
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        startFlow(clientRequestId)
        awaitRpcFlowFinished(bobHoldingId, clientRequestId)

        val wsHandler = MessageQueueWebSocketHandler()
        val client = SmokeTestWebsocketClient()

        client.start()
        client.connect(flowStatusFeedPath, wsHandler)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        client.use {
            eventually {
                assertThat(wsHandler.messageQueueSnapshot).hasSize(1)
            }
            assertThat(wsHandler.messageQueueSnapshot[0]).contains(FlowStates.COMPLETED.name)

            eventually {
                assertFalse(wsHandler.isConnected)
            }
        }

        // Check HTTP RPC Server is still functioning
        assertThat(getFlowClasses(bobHoldingId)).contains(SMOKE_TEST_CLASS_NAME)
    }

    @Order(41)
    @Test
    fun `two test clients can function after first reports completed flow during registration`() {
        val clientRequestId = generateRequestId("test41")
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        startFlow(clientRequestId)
        awaitRpcFlowFinished(bobHoldingId, clientRequestId)

        val wsHandler1 = MessageQueueWebSocketHandler()
        val client1 = SmokeTestWebsocketClient()

        client1.start()
        val session1 = client1.connect(flowStatusFeedPath, wsHandler1)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        eventually {
            assertThat(wsHandler1.messageQueueSnapshot).hasSize(1)
        }
        assertThat(wsHandler1.messageQueueSnapshot[0]).contains(FlowStates.COMPLETED.name)

        eventually {
            assertFalse(wsHandler1.isConnected)
        }

        session1.close(1000, "Smoke test closing session 1.")
        client1.close()

        val wsHandler2 = MessageQueueWebSocketHandler()
        val client2 = SmokeTestWebsocketClient()

        client2.start()
        val session2 = client2.connect(flowStatusFeedPath, wsHandler2)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        eventually {
            assertThat(wsHandler2.messageQueueSnapshot).hasSize(1)
        }
        assertThat(wsHandler2.messageQueueSnapshot[0]).contains(FlowStates.COMPLETED.name)

        eventually {
            assertFalse(wsHandler2.isConnected)
        }

        session2.close(1000, "Smoke test closing session 2.")
        client2.close()
    }

    @Order(50)
    @Test
    fun `websocket connection terminated when client sends server a message`() {
        val clientRequestId = generateRequestId("test50")
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        useWebsocketConnection(flowStatusFeedPath) { wsHandler ->
            wsHandler.send("malicious message!")
            eventually {
                assertFalse(wsHandler.isConnected())
            }
        }
    }

    @Order(51)
    @Test
    fun `two websocket connections correct one terminated when it sends server a message`() {
        val clientRequestId1 = generateRequestId("test51-req1")
        val clientRequestId2 = generateRequestId("test51-req2")
        val flowStatusFeedPath1 = "/flow/$bobHoldingId/$clientRequestId1"
        val flowStatusFeedPath2 = "/flow/$bobHoldingId/$clientRequestId2"

        val wsHandler1 = MessageQueueWebSocketHandler()
        val client1 = SmokeTestWebsocketClient()
        client1.start()
        val session1 = client1.connect(flowStatusFeedPath1, wsHandler1)

        val wsHandler2 = MessageQueueWebSocketHandler()
        val client2 = SmokeTestWebsocketClient()
        client2.start()
        val session2 = client2.connect(flowStatusFeedPath2, wsHandler2)

        wsHandler1.send("malicious message for client 1 ($clientRequestId1)")

        eventually {
            assertFalse(wsHandler1.isConnected)
        }

        session1.close()
        session2.close()
        client1.close()
        client2.close()
    }

    @Order(60)
    @Test
    fun `websocket connection terminated when client registers for holding identity with invalid holding identity hex string`() {
        val clientRequestId = generateRequestId("test60")
        val flowStatusFeedPath = "/flow/THIS_HOLDING_ID_IS_NOT_HEX/$clientRequestId"

        val wsHandler = MessageQueueWebSocketHandler()
        val client = SmokeTestWebsocketClient()
        client.start()
        client.connect(flowStatusFeedPath, wsHandler)
        eventually {
            assertFalse(wsHandler.isConnected)
        }
    }

    @Order(61)
    @Test
    fun `websocket connection terminated when client registers for non existing holding identity`() {
        val clientRequestId = generateRequestId("test61")
        val flowStatusFeedPath = "/flow/544849535f484f4c44494e475f49445f49535f4e4f545f484558/$clientRequestId"

        val wsHandler = MessageQueueWebSocketHandler()
        val client = SmokeTestWebsocketClient()
        client.start()
        client.connect(flowStatusFeedPath, wsHandler)
        eventually {
            assertFalse(wsHandler.isConnected)
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