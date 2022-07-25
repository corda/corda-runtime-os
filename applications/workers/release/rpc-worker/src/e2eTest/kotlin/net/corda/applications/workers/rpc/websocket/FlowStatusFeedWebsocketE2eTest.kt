package net.corda.applications.workers.rpc.websocket

import java.time.Duration
import java.util.LinkedList
import java.util.NoSuchElementException
import net.corda.applications.workers.rpc.http.SkipWhenRpcEndpointUnavailable
import net.corda.flow.rpcops.v1.types.response.FlowStatusResponse
import net.corda.v5.base.util.contextLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@SkipWhenRpcEndpointUnavailable
class FlowStatusFeedWebsocketE2eTest {

    private companion object {
        val log = contextLogger()
    }

    @Test
    fun tryItOut() {

        val holderId = "holderId"
        val clientReqId = "1"
        val flowStatusFeedPath = "/flow/$holderId/$clientReqId"

        val receivedMessages = LinkedList<FlowStatusResponse>()
        val wsHandler = FlowStatusWebsocketHandler(receivedMessages)

        // todo conal - also add in the http client to call flow start api
        E2eWebsocketClient(wsHandler).use { client ->
            client.start()
            client.connect(flowStatusFeedPath)

            waitOnNextMessage(receivedMessages) { message ->
                assertEquals(message.flowStatus, E2eFlowStates.START_REQUESTED.name)
            }
        }

    }

    private enum class E2eFlowStates { START_REQUESTED, RUNNING, RETRYING, COMPLETED, FAILED }
}

fun <T, R> waitOnNextMessage(
    queue: LinkedList<T>,
    duration: Duration = Duration.ofSeconds(10),
    waitBetween: Duration = Duration.ofMillis(100),
    waitBefore: Duration = Duration.ZERO,
    test: (message: T) -> R
): R {
    val end = System.nanoTime() + duration.toNanos()
    var times = 0
    var lastFailure: Exception? = null

    if (!waitBefore.isZero) Thread.sleep(waitBefore.toMillis())

    while (System.nanoTime() < end) {
        try {
            return test(queue.pop())
        } catch (e: NoSuchElementException) {
            if (!waitBetween.isZero) Thread.sleep(waitBetween.toMillis())
            lastFailure = e
        }
        times++
    }

    throw AssertionError("Test failed with \"${lastFailure?.message}\" after $duration; attempted $times times", lastFailure)
}