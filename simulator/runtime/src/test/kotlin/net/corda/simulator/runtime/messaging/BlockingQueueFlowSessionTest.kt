package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.exceptions.SessionAlreadyClosedException
import net.corda.simulator.runtime.testflows.PingAckMessage
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
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

    private val fakeClock = mock<Clock>()
    private val fakeClockConfiguration = mock<SimulatorConfiguration>()

    init {
        whenever(flowCallConfiguration.pollInterval).doReturn(Duration.ofMillis(100))
        whenever(flowCallConfiguration.timeout).doReturn(Duration.ofMinutes(1))
        whenever(flowCallConfiguration.clock).doReturn(Clock.systemDefaultZone())

        whenever(fakeClockConfiguration.timeout).doReturn(Duration.ofMinutes(5))
        whenever(fakeClockConfiguration.pollInterval).doReturn(Duration.ofMillis(100))
        whenever(fakeClockConfiguration.clock).doReturn(fakeClock)
    }

    @Test
    fun `should retrieve a response from the common queue and pass it back to the sender`() {
        // Given a session constructed with shared queues
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BaseInitiatorFlowSession(
            FlowContext(flowCallConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        val receivingSession = BaseResponderFlowSession(
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
    fun `should throw an exception if the session is used after closing, except for closing again`() {
        // Given sessions constructed with shared queues
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val initiatorSession = BaseInitiatorFlowSession(
            FlowContext(flowCallConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        val responderSession = BaseResponderFlowSession(
            FlowContext(flowCallConfiguration, receiver, "ping-ack"),
            fromInitiatorToResponder,
            fromResponderToInitiator
        )

        // When we close them
        responderSession.close()
        initiatorSession.responderClosed()
        initiatorSession.close()

        // Then subsequent calls should error
        assertThrows<SessionAlreadyClosedException> { initiatorSession.send("ping-ack") }
        assertThrows<SessionAlreadyClosedException> { initiatorSession.receive() }

        assertThrows<SessionAlreadyClosedException> { responderSession.send("ping-ack") }
        assertThrows<SessionAlreadyClosedException> { responderSession.receive() }

        // Except for closing again
        assertDoesNotThrow { initiatorSession.close() }
        assertDoesNotThrow { responderSession.close() }
    }



    @Test
    fun `should time out receive if flow does not complete`() {
        // Given a session constructed only on the sending side
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BaseInitiatorFlowSession(
            FlowContext(fakeClockConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        // When we advance the clock past the 5 minute fake timeout
        whenever(fakeClock.instant()).thenReturn(Instant.now())
        thread {
            sleep(100)
            whenever(fakeClock.instant()).thenReturn(Instant.now().plusSeconds(60*6))
        }

        // Then the session should time out from any receive
        assertThrows<TimeoutException> {
            sendingSession.receive<Any>()
        }
    }

    @Test
    fun `should time out close if flow does not complete`() {
        // Given a session constructed only on the sending side
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BaseInitiatorFlowSession(
            FlowContext(fakeClockConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        // When we advance the clock past the 5 minute fake timeout
        whenever(fakeClock.instant()).thenReturn(Instant.now())
        thread {
            sleep(100)
            whenever(fakeClock.instant()).thenReturn(Instant.now().plusSeconds(60*6))
        }

        // Then the session should time out from any close
        assertThrows<TimeoutException> {
            sendingSession.close()
        }
    }

    @Test
    fun `should wait for responder sessions to close`() {
        // Given a session constructed only on the sending side
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BaseInitiatorFlowSession(
            FlowContext(flowCallConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        // When we close the responder
        var responderClosed = false
        thread {
            sleep(50)
            responderClosed = true
            sendingSession.responderClosed()
        }

        // Then the sending session should wait until it's closed before closing itself
        sendingSession.close()
        assertTrue(responderClosed, "Responder was not closed when sending session closed")
    }
}