package net.corda.applications.workers.smoketest.websocket

import java.time.Duration
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
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
import org.junit.jupiter.api.AfterEach
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

    @Order(10)
    @Test
    fun `websocket connection can be opened to listen for updates for flow clientRequestid`() {
        val flowStatusFeedPath = "/flow/$bobHoldingId/${UUID.randomUUID()}"

        val wsHandler = MessageQueueWebsocketHandler(LinkedList())

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
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        useWebsocketConnection(flowStatusFeedPath) { wsHandler ->
            startFlow(clientRequestId)

            eventually(Duration.ofSeconds(10)) {
                assertThat(wsHandler.messageQueue).hasSize(3)
                assertThat(wsHandler.messageQueue.poll()).contains(FlowStates.START_REQUESTED.name)
                assertThat(wsHandler.messageQueue.poll()).contains(FlowStates.RUNNING.name)
                assertThat(wsHandler.messageQueue.poll()).contains(FlowStates.COMPLETED.name)
            }
        }
    }

    @Order(30)
    @Test
    fun `multiple websocket connections can be open for one flow from one holding identity and request id`() {
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        useWebsocketConnection(flowStatusFeedPath) { wsHandler1 ->
            useWebsocketConnection(flowStatusFeedPath) { wsHandler2 ->
                startFlow(clientRequestId)

                eventually(Duration.ofSeconds(10)) {
                    assertThat(wsHandler1.messageQueue).hasSize(3)
                    assertThat(wsHandler1.messageQueue.poll()).contains(FlowStates.START_REQUESTED.name)
                    assertThat(wsHandler1.messageQueue.poll()).contains(FlowStates.RUNNING.name)
                    assertThat(wsHandler1.messageQueue.poll()).contains(FlowStates.COMPLETED.name)
                    assertThat(wsHandler2.messageQueue).hasSize(3)
                    assertThat(wsHandler2.messageQueue.poll()).contains(FlowStates.START_REQUESTED.name)
                    assertThat(wsHandler2.messageQueue.poll()).contains(FlowStates.RUNNING.name)
                    assertThat(wsHandler2.messageQueue.poll()).contains(FlowStates.COMPLETED.name)
                }
            }
        }
    }

    @Order(40)
    @Test
    fun `registering for flow status feed when flow is already finished sends the finished status and terminates connection`() {
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        startFlow(clientRequestId)
        awaitRpcFlowFinished(bobHoldingId, clientRequestId)

        val messageQueue = ConcurrentLinkedQueue<String>()
        val wsHandler = MessageQueueWebsocketHandler(messageQueue)
        val client = SmokeTestWebsocketClient()

        client.start()
        client.connect(flowStatusFeedPath, wsHandler)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        client.use {
            eventually {
                assertThat(wsHandler.messageQueue).hasSize(1)
                assertThat(wsHandler.messageQueue.poll()).contains(FlowStates.COMPLETED.name)
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
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        startFlow(clientRequestId)
        awaitRpcFlowFinished(bobHoldingId, clientRequestId)

        val messageQueue1 = ConcurrentLinkedQueue<String>()
        val wsHandler1 = MessageQueueWebsocketHandler(messageQueue1)
        val client1 = SmokeTestWebsocketClient()

        client1.start()
        val session1 = client1.connect(flowStatusFeedPath, wsHandler1)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        eventually {
            assertThat(wsHandler1.messageQueue).hasSize(1)
            assertThat(wsHandler1.messageQueue.poll()).contains(FlowStates.COMPLETED.name)
        }

        eventually {
            assertFalse(wsHandler1.isConnected)
        }

        session1.close(1000, "Smoke test closing session 1.")
        client1.close()

        val messageQueue2 = ConcurrentLinkedQueue<String>()
        val wsHandler2 = MessageQueueWebsocketHandler(messageQueue2)
        val client2 = SmokeTestWebsocketClient()

        client2.start()
        val session2 = client2.connect(flowStatusFeedPath, wsHandler2)
        // The websocket channel is terminated too quickly to use eventually to assert wsHandler.isConnected

        eventually {
            assertThat(wsHandler2.messageQueue).hasSize(1)
            assertThat(wsHandler2.messageQueue.poll()).contains(FlowStates.COMPLETED.name)
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
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        useWebsocketConnection(flowStatusFeedPath) { wsHandler ->
            wsHandler.send("malicious message!")
            eventually {
                assertFalse(wsHandler.isConnected())
            }
        }
    }

    @Order(60)
    @Test
    fun `websocket connection terminated when client registers for holding identity with invalid holding identity hex string`() {
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/THIS_HOLDING_ID_IS_NOT_HEX/$clientRequestId"

        val wsHandler = MessageQueueWebsocketHandler(ConcurrentLinkedQueue())
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
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/544849535f484f4c44494e475f49445f49535f4e4f545f484558/$clientRequestId"

        val wsHandler = MessageQueueWebsocketHandler(ConcurrentLinkedQueue())
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