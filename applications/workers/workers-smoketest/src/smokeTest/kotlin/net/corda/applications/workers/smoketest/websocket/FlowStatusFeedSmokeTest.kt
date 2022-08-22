package net.corda.applications.workers.smoketest.websocket

import java.time.Duration
import java.util.UUID
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.SMOKE_TEST_CLASS_NAME
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.getFlowClasses
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.websocket.client.MessageQueueWebsocketHandler
import net.corda.applications.workers.smoketest.websocket.client.SmokeTestWebsocketClient
import net.corda.applications.workers.smoketest.websocket.client.useWebsocketConnection
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

// This test relies on `VirtualNodeRpcTest` and `FlowTest` to run first which will create vNodes necessary to run this test
@Order(30)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FlowStatusFeedSmokeTest {

    private companion object {
        val bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
    }

    private enum class FlowStates { START_REQUESTED, RUNNING, RETRYING, COMPLETED, FAILED }

    private fun generateRequestId(identifyingTest: String): String {
        return "$identifyingTest-${UUID.randomUUID()}"
    }

    @Order(10)
    @Test
    fun `websocket connection can be opened to listen for updates for flow clientRequestid`() {
        val flowStatusFeedPath = "/flow/$bobHoldingId/${generateRequestId("test10")}"

        val wsHandler = MessageQueueWebsocketHandler()

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
                assertThat(wsHandler.messageQueue.size).isEqualTo(3)
            }
            eventually {
                assertThat(wsHandler.messageQueue[0]).contains(FlowStates.START_REQUESTED.name)
                assertThat(wsHandler.messageQueue[1]).contains(FlowStates.RUNNING.name)
                assertThat(wsHandler.messageQueue[2]).contains(FlowStates.COMPLETED.name)
            }
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
                    assertThat(wsHandler1.messageQueue.size).isEqualTo(3)
                    assertThat(wsHandler2.messageQueue.size).isEqualTo(3)
                }
                eventually {
                    assertThat(wsHandler1.messageQueue[0]).contains(FlowStates.START_REQUESTED.name)
                    assertThat(wsHandler1.messageQueue[1]).contains(FlowStates.RUNNING.name)
                    assertThat(wsHandler1.messageQueue[2]).contains(FlowStates.COMPLETED.name)
                    assertThat(wsHandler2.messageQueue[0]).contains(FlowStates.START_REQUESTED.name)
                    assertThat(wsHandler2.messageQueue[1]).contains(FlowStates.RUNNING.name)
                    assertThat(wsHandler2.messageQueue[2]).contains(FlowStates.COMPLETED.name)
                }
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
                    assertThat(wsHandler1.messageQueue.size).isEqualTo(3)
                    assertThat(wsHandler2.messageQueue.size).isEqualTo(3)
                }

                eventually {
                    assertNormalFlowStatusesForRequest(wsHandler1.messageQueue, clientRequestId1)
                    assertNormalFlowStatusesForRequest(wsHandler2.messageQueue, clientRequestId2)
                }
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

        val wsHandler = MessageQueueWebsocketHandler()
        val client = SmokeTestWebsocketClient()

        client.start()
        client.connect(flowStatusFeedPath, wsHandler)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        client.use {
            eventually {
                assertThat(wsHandler.messageQueue.size).isEqualTo(1)
            }
            eventually {
                assertThat(wsHandler.messageQueue[0]).contains(FlowStates.COMPLETED.name)
            }

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

        val wsHandler1 = MessageQueueWebsocketHandler()
        val client1 = SmokeTestWebsocketClient()

        client1.start()
        val session1 = client1.connect(flowStatusFeedPath, wsHandler1)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        eventually {
            assertThat(wsHandler1.messageQueue).hasSize(1)
        }

        eventually {
            assertThat(wsHandler1.messageQueue[0]).contains(FlowStates.COMPLETED.name)
        }

        eventually {
            assertFalse(wsHandler1.isConnected)
        }

        session1.close(1000, "Smoke test closing session 1.")
        client1.close()

        val wsHandler2 = MessageQueueWebsocketHandler()
        val client2 = SmokeTestWebsocketClient()

        client2.start()
        val session2 = client2.connect(flowStatusFeedPath, wsHandler2)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        eventually {
            assertThat(wsHandler2.messageQueue).hasSize(1)
        }

        eventually {
            assertThat(wsHandler2.messageQueue[0]).contains(FlowStates.COMPLETED.name)
        }

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

        val wsHandler1 = MessageQueueWebsocketHandler()
        val client1 = SmokeTestWebsocketClient()
        client1.start()
        val session1 = client1.connect(flowStatusFeedPath1, wsHandler1)

        val wsHandler2 = MessageQueueWebsocketHandler()
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

        val wsHandler = MessageQueueWebsocketHandler()
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

        val wsHandler = MessageQueueWebsocketHandler()
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