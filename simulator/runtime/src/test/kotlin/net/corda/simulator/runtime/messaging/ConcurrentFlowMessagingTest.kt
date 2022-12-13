package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.exceptions.SessionAlreadyClosedException
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.testflows.PingAckMessage
import net.corda.simulator.runtime.testflows.PingAckResponderFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.thread

@Timeout(5000)
class ConcurrentFlowMessagingTest {

    private val senderX500 = MemberX500Name.parse("CN=ISendMessages, OU=Application, O=R3, L=London, C=GB")
    private val receiverX500 = MemberX500Name.parse("CN=IReceiveMessages, OU=Application, O=R3, L=London, C=GB")
    private val configuration = mock<SimulatorConfiguration>()

    @BeforeEach
    fun `initialize configuration`() {
        whenever(configuration.clock).doReturn(Clock.systemDefaultZone())
        whenever(configuration.timeout).doReturn(Duration.ofMinutes(1))
        whenever(configuration.pollInterval).doReturn(Duration.ofMillis(100))
    }

    @Test
    fun `can initiate a flow, inject services, send a message and receive a message`() {
        // Given a message that we want to send
        val message = PingAckMessage("Ping")

        // And a factory that will build our responder
        val flowFactory = mock<FlowFactory>()
        val responderFlow = PingAckResponderFlow()
        whenever(flowFactory.createResponderFlow(receiverX500, PingAckResponderFlow::class.java))
            .thenReturn(responderFlow)

        // And flow messaging that can open sessions to the other side,
        // looking them up in the fiber
        val fiber = mock<SimFiber>()

        // And a sender and receiver that will be returned by the fiber
        whenever(fiber.lookUpResponderClass(receiverX500, "ping-ack"))
            .thenReturn(PingAckResponderFlow::class.java)

        // And an injector that will inject services
        val injector = mock<FlowServicesInjector>()

        // When we initiate the flow
        val flowMessaging = ConcurrentFlowMessaging(
            FlowContext(configuration, senderX500, "ping-ack"),
            fiber,
            injector,
            flowFactory
        )
        val sendingSession = flowMessaging.initiateFlow(receiverX500)

        // Then the counterparty should be correctly set
        assertThat(sendingSession.counterparty, `is`(receiverX500))

        // Then it should have injected the services into the responder
        verify(injector, times(1)).injectServices(
            eq(responderFlow),
            eq(receiverX500),
            eq(fiber),
            eq(flowFactory)
        )

        // When we send and receive the message
        thread { sendingSession.send(message) }
        val received = sendingSession.receive(PingAckMessage::class.java)

        // Then it should come through OK with the right counterparty on the session
        // (as put into the message)
        assertThat(received, `is`(PingAckMessage("Ack to $senderX500")))
    }

    @Test
    fun `can initialize a concrete instance of a responder flow`() {
        // Given a message that we want to send
        val message = PingAckMessage("Ping")

        // And a factory that will not be used, with a responder that's already been created
        val flowFactory = mock<FlowFactory>()
        val responderFlow = IckResponderFlow()

        // And flow messaging that can open sessions to the other side,
        // looking them up in the fiber
        val flowAndServiceLookUp = mock<SimFiber>()

        // And a sender and receiver that will be returned by the fiber
        whenever(flowAndServiceLookUp.lookUpResponderInstance(receiverX500, "ping-ack"))
            .thenReturn(responderFlow)

        // And an injector that will inject services
        val injector = mock<FlowServicesInjector>()

        // When we initiate the flow
        val flowMessaging = ConcurrentFlowMessaging(
            FlowContext(configuration, senderX500, "ping-ack"),
            flowAndServiceLookUp,
            injector,
            flowFactory
        )
        val sendingSession = flowMessaging.initiateFlow(receiverX500)

        // Then it should have injected the services into the responder
        verify(injector, times(1)).injectServices(
            eq(responderFlow),
            eq(receiverX500),
            eq(flowAndServiceLookUp),
            eq(flowFactory)
        )

        // When we send and receive the message
        thread { sendingSession.send(message) }
        val received = sendingSession.receive(PingAckMessage::class.java)

        // Then it should come through OK
        assertThat(received, `is`(PingAckMessage("Ick")))
    }

    @Test
    fun `should set the error on an initiating flow session when a responder flow throws it`() {
        // Given a factory and injector that will not be used, with a responder that's already been created
        // where the responder will throw an error
        val flowFactory = mock<FlowFactory>()
        val injector = mock<FlowServicesInjector>()

        val responderFlow = YuckResponderFlow()

        // And flow messaging that can open sessions to the other side,
        // looking them up in the fiber
        val flowAndServiceLookUp = mock<SimFiber>()

        // And a sender and receiver that will be returned by the fiber
        whenever(flowAndServiceLookUp.lookUpResponderInstance(receiverX500, "ping-ack"))
            .thenReturn(responderFlow)

        val flowMessaging = ConcurrentFlowMessaging(
            FlowContext(configuration, senderX500, "ping-ack"),
            flowAndServiceLookUp,
            injector,
            flowFactory
        )
        val sendingSession = flowMessaging.initiateFlow(receiverX500)

        // When we receive on the sending flow
        // Then it should rethrow the error (note Real Corda will not contain the original error)
        assertThrows<ResponderFlowException> {
            sendingSession.sendAndReceive(Any::class.java, PingAckMessage("Ping"))
        }
    }

    @Test
    fun `should close all sessions it created when closed`() {
        // Given flow messaging that has created two session pairs
        val alice = MemberX500Name.parse("CN=alice, OU=Application, O=R3, L=London, C=GB")
        val bob = MemberX500Name.parse("CN=bob, OU=Application, O=R3, L=London, C=GB")
        val charlie = MemberX500Name.parse("CN=bob, OU=Application, O=R3, L=London, C=GB")

        val sessionPair1 = SessionPair(mock(), mock())
        val sessionPair2 = SessionPair(mock(), mock())
        val sessionFactory = mock<SessionFactory>()
        whenever(sessionFactory.createSessions(any(), any())).thenReturn(sessionPair1, sessionPair2)

        // And instance flows (just because they're easier and not what we care about here)
        val flowAndServiceLookUp = mock<SimFiber>()

        whenever(flowAndServiceLookUp.lookUpResponderInstance(any(), eq("ping-ack")))
            .thenReturn(IckResponderFlow(), IckResponderFlow())

        val flowMessaging = ConcurrentFlowMessaging(
            FlowContext(configuration, alice, "ping-ack"),
            flowAndServiceLookUp,
            mock(),
            mock(),
            sessionFactory
        )

        // When we initialize sessions, then close the flow messaging
        flowMessaging.initiateFlow(bob)
        flowMessaging.initiateFlow(charlie)
        flowMessaging.close()

        // Then the sessions should be closed too
        listOf(sessionPair1.responderSession, sessionPair2.responderSession).forEach {
            verify(it, atLeastOnce()).close()
        }

        listOf(sessionPair1.initiatorSession,sessionPair2.initiatorSession,).forEach {
            verify(it, atLeastOnce()).close()
        }
    }

    @Test
    fun `should close responder session when responder call completes`() {
        // Given an instance flow
        val flowAndServiceLookUp = mock<SimFiber>()
        val responderFlow = SendingResponderFlow()
        whenever(flowAndServiceLookUp.lookUpResponderInstance(any(), eq("ping-ack")))
            .thenReturn(responderFlow)

        // When we initialize the session and make the call
        val flowMessaging = ConcurrentFlowMessaging(
            FlowContext(configuration, senderX500, "ping-ack"),
            flowAndServiceLookUp,
            mock(),
            mock()
        )

        // When we call the responder session and wait for it to close (closing our initiator session will wait)
        val session = flowMessaging.initiateFlow(receiverX500)
        session.receive(Any::class.java)
        session.close()

        // Then the responder session should have been closed without timeout
        assertThrows<SessionAlreadyClosedException> { responderFlow.capturedSession!!.send("") }
    }

    class IckResponderFlow : ResponderFlow {
        override fun call(session: FlowSession) {
            session.send(PingAckMessage("Ick"))
        }
    }

    class YuckResponderFlow : ResponderFlow {
        override fun call(session: FlowSession) {
            error("This error should be propagated to the initiator thread")
        }
    }

    class SendingResponderFlow : ResponderFlow {
        var capturedSession : FlowSession? = null
        override fun call(session: FlowSession) {
            capturedSession = session
            session.send(Unit)
        }
    }
}
