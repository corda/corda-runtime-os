package net.corda.applications.workers.smoketest.websocket

import java.time.Duration
import java.util.LinkedList
import java.util.UUID
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.RpcSmokeTestInput
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.websocket.client.MessageQueueWebsocketHandler
import net.corda.applications.workers.smoketest.websocket.client.SmokeTestWebsocketClient
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

        val messageQueue = LinkedList<String>()
        val wsHandler = MessageQueueWebsocketHandler(messageQueue)

        val client = SmokeTestWebsocketClient(wsHandler)
        client.start()
        client.connect(flowStatusFeedPath)

        eventually {
            assertThat(wsHandler.isConnected)
        }

        startFlow(clientRequestId)

        eventually(Duration.ofSeconds(10)) {
            assertThat(messageQueue).hasSize(2)
//            assertThat(messageQueue[0]).isEqualTo(FlowStates.RUNNING.name)
//            assertThat(messageQueue[1]).isEqualTo(FlowStates.COMPLETED.name)
        }

//        waitOnNextMessage(messageQueue) { flowStatus ->
//            assertThat(flowStatus.flowStatus).isEqualTo(FlowStates.RUNNING)
//        }
//        waitOnNextMessage(messageQueue) { flowStatus ->
//            assertThat(flowStatus.flowStatus).isEqualTo(FlowStates.COMPLETED)
//        }
        // should the connection automatically get closed when the server closes the connection?
//        client.close()
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