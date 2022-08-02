package net.corda.applications.workers.smoketest.websocket

import java.time.Duration
import java.util.LinkedList
import java.util.Queue
import java.util.UUID
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.websocket.client.MessageQueueWebsocketHandler
import net.corda.applications.workers.smoketest.websocket.client.SmokeTestWebsocketClient
import net.corda.applications.workers.smoketest.websocket.client.runWithWebsocketConnection
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@Order(30)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FlowStatusFeedSmokeTest {

    private companion object {
        var bobHoldingId: String = getHoldingIdShortHash(X500_BOB, GROUP_ID)
    }

    private enum class FlowStates { START_REQUESTED, RUNNING, RETRYING, COMPLETED, FAILED }

    @Order(10)
    @Test
    fun `websocket connection can be opened to listen for updates for flow clientRequestid`() {
        val flowStatusFeedPath = "/flow/$bobHoldingId/${UUID.randomUUID()}"

        val wsHandler = MessageQueueWebsocketHandler(LinkedList())

        val client = SmokeTestWebsocketClient(wsHandler)
        client.start()
        client.connect(flowStatusFeedPath)
        eventually {
            assertThat(wsHandler.isConnected)
        }

        client.close()
        eventually {
            assertThat(wsHandler.isNotConnected)
        }
    }

    @Order(20)
    @Test
    fun `flow status update feed receives updates for flow lifecycle`() {
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        runWithWebsocketConnection(flowStatusFeedPath) { messageQueue ->
            startFlow(clientRequestId)

            eventually(Duration.ofSeconds(10)) {
                assertThat(messageQueue).hasSize(3)
                assertThat(messageQueue.poll()).contains(FlowStates.START_REQUESTED.name)
                assertThat(messageQueue.poll()).contains(FlowStates.RUNNING.name)
                assertThat(messageQueue.poll()).contains(FlowStates.COMPLETED.name)
            }
        }
    }

    @Order(30)
    @Test
    fun `multiple websocket connections can be open for one flow from one holding identity and request id`() {
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        runWithWebsocketConnection(flowStatusFeedPath) { messageQueue1 ->
            runWithWebsocketConnection(flowStatusFeedPath) { messageQueue2 ->
                startFlow(clientRequestId)

                eventually(Duration.ofSeconds(10)) {
                    assertThat(messageQueue1).hasSize(3)
                    assertThat(messageQueue1.poll()).contains(FlowStates.START_REQUESTED.name)
                    assertThat(messageQueue1.poll()).contains(FlowStates.RUNNING.name)
                    assertThat(messageQueue1.poll()).contains(FlowStates.COMPLETED.name)
                    assertThat(messageQueue2).hasSize(3)
                    assertThat(messageQueue2.poll()).contains(FlowStates.START_REQUESTED.name)
                    assertThat(messageQueue2.poll()).contains(FlowStates.RUNNING.name)
                    assertThat(messageQueue2.poll()).contains(FlowStates.COMPLETED.name)
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

        val messageQueue: Queue<String> = LinkedList()
        val wsHandler = MessageQueueWebsocketHandler(messageQueue)
        val client = SmokeTestWebsocketClient(wsHandler)
        client.start()
        client.connect(flowStatusFeedPath)

        eventually(Duration.ofSeconds(10)) {
            assertThat(messageQueue).hasSize(1)
            assertThat(messageQueue.poll()).contains(FlowStates.COMPLETED.name)
        }

        eventually {
            assertThat(wsHandler.isNotConnected)
        }
        client.close()
    }

    @Order(50)
    @Test
    fun `websocket connection terminated when client sends server a message`() {
        val clientRequestId = UUID.randomUUID().toString()
        val flowStatusFeedPath = "/flow/$bobHoldingId/$clientRequestId"

        val wsHandler = MessageQueueWebsocketHandler(LinkedList())

        val client = SmokeTestWebsocketClient(wsHandler)
        client.start()
        client.connect(flowStatusFeedPath)

        eventually {
            assertThat(wsHandler.isConnected)
        }

        wsHandler.send("malicious message!")

        eventually {
            assertThat(wsHandler.isNotConnected)
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