package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.runtime.testflows.PingAckMessage
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.Thread.sleep
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread


@Timeout(value = 5, unit = TimeUnit.SECONDS)
class BlockingQueueFlowSessionTest {

    private val sender = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
    private val receiver = MemberX500Name.parse("CN=IRunCorDappsToo, OU=Application, O=R3, L=London, C=GB")

    private val flowCallConfiguration = mock<SimulatorConfiguration>()

    init {
        whenever(flowCallConfiguration.pollInterval).doReturn(Duration.ofMillis(100))
        whenever(flowCallConfiguration.timeout).doReturn(Duration.ofMinutes(1))
        whenever(flowCallConfiguration.clock).doReturn(Clock.systemDefaultZone())
    }

    @Test
    fun `should retrieve a response from the common queue and pass it back to the sender`() {
        // Given a session constructed with shared queues
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BlockingQueueFlowSession(
            FlowContext(flowCallConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        val receivingSession = BlockingQueueFlowSession(
            FlowContext(flowCallConfiguration, receiver, "ping-ack"),
            fromInitiatorToResponder,
            fromResponderToInitiator
        )

        // When we send a message
        val payload = PingAckMessage("Ping")
        sendingSession.send(payload)

        // Then we should be able to return the response that appears in the queue
        assertThat(receivingSession.receive<PingAckMessage>().message, `is`("Ping"))
    }

    @Test
    fun `should throw an exception if receivedException is set`() {
        // Given a session constructed only on the sending side
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BlockingQueueFlowSession(
            FlowContext(flowCallConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        // When we send a message and then set a received exception
        thread {
            sleep(100)
            sendingSession.responderErrorCaught(IllegalArgumentException("Just because"))
        }

        assertThrows<ResponderFlowException> {
            sendingSession.receive<Any>()
        }
    }

    @Test
    fun `should time out if flow does not complete`() {
        // Given a session constructed only on the sending side
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val fakeClockConfiguration = mock<SimulatorConfiguration>()

        val sendingSession = BlockingQueueFlowSession(
            FlowContext(fakeClockConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        // With a timeout of 5 minutes (for a demo)
        val clock = mock<Clock>()
        whenever(fakeClockConfiguration.timeout).doReturn(Duration.ofMinutes(5))
        whenever(fakeClockConfiguration.pollInterval).doReturn(Duration.ofMillis(100))
        whenever(fakeClockConfiguration.clock).doReturn(clock)

        whenever(clock.instant()).thenReturn(Instant.now())

        // When we advance the clock past 5 minutes
        thread {
            sleep(100)
            whenever(clock.instant()).thenReturn(Instant.now().plusSeconds(60*6))
        }

        // Then the session should time out
        assertThrows<TimeoutException> {
            sendingSession.receive<Any>()
        }
    }
}