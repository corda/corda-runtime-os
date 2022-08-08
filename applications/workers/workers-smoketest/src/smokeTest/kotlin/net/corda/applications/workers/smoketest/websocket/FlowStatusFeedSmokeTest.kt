package net.corda.applications.workers.smoketest.websocket

import java.time.Duration
import java.util.LinkedList
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
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

    // todo conal - could it be this test all along? two clients? Maybe it passes and then something goes wrong when closing both clients
    /*@Order(30)
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
    }*/

    /*@Order(31)
    @Test
    fun `one test client can have multiple websocket connections open for different flows`() {
        val clientRequestId1 = UUID.randomUUID().toString()
        val clientRequestId2 = UUID.randomUUID().toString()
        val flowStatusFeedPath1 = "/flow/$bobHoldingId/$clientRequestId1"
        val flowStatusFeedPath2 = "/flow/$bobHoldingId/$clientRequestId2"

        val messageQueue1 = ConcurrentLinkedQueue<String>()
        val messageQueue2 = ConcurrentLinkedQueue<String>()
        val wsHandler1 = MessageQueueWebsocketHandler(messageQueue1)
        val wsHandler2 = MessageQueueWebsocketHandler(messageQueue2)
        val client = SmokeTestWebsocketClient()

        client.start()
        val session1 = client.connect(flowStatusFeedPath1, wsHandler1)
        val session2 = client.connect(flowStatusFeedPath2, wsHandler2)

        eventually {
            assertTrue(session1.isOpen)
            assertTrue(session2.isOpen)
        }

        startFlow(clientRequestId1)
        awaitRpcFlowFinished(bobHoldingId, clientRequestId1)

        startFlow(clientRequestId2)
        awaitRpcFlowFinished(bobHoldingId, clientRequestId2)

        eventually(Duration.ofSeconds(10)) {
            assertThat(messageQueue1).hasSize(3)
            assertThat(messageQueue2).hasSize(3)
            assertThat(messageQueue1.poll()).contains(FlowStates.START_REQUESTED.name)
            assertThat(messageQueue1.poll()).contains(FlowStates.RUNNING.name)
            assertThat(messageQueue1.poll()).contains(FlowStates.COMPLETED.name)
            assertThat(messageQueue2.poll()).contains(FlowStates.START_REQUESTED.name)
            assertThat(messageQueue2.poll()).contains(FlowStates.RUNNING.name)
            assertThat(messageQueue2.poll()).contains(FlowStates.COMPLETED.name)
        }

        session1.close(1000, "CONAL closed 1")
        session2.close(1000, "CONAL closed 2")
        client.close()
    }*/
/*

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
    }
*/

    /*@Order(41)
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

        session1.close(1000, "CONAL closed 1")
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

        session2.close(1000, "CONAL closed 2")
        client2.close()
    }*/

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

    private fun startFlow(clientRequestId: String) {
        val requestBody = RpcSmokeTestInput().apply {
            command = "echo"
            data = mapOf("echo_value" to "hello")
        }

        startRpcFlow(holdingId = bobHoldingId, args = requestBody, requestId = clientRequestId)
    }
}