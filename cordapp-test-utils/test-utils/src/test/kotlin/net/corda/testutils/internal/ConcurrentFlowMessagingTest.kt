package net.corda.testutils.internal

import net.corda.testutils.flows.PingAckFlow
import net.corda.testutils.flows.PingAckMessage
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.concurrent.thread

class ConcurrentFlowMessagingTest {

    private val senderX500 = MemberX500Name.parse("CN=ISendMessages, OU=Application, O=R3, L=London, C=GB")
    private val receiverX500 = MemberX500Name.parse("CN=IReceiveMessages, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `can initiate a flow, inject services, send a message and receive a message`() {
        // Given a message that we want to send
        val message = PingAckMessage("Ping")

        // And a factory that will build our responder
        val flowFactory = mock<FlowFactory>()
        val responderFlow = IckResponderFlow()
        whenever(flowFactory.createResponderFlow(receiverX500, IckResponderFlow::class.java))
            .thenReturn(responderFlow)

        // And flow messaging that can open sessions to the other side,
        // looking them up in the fiber
        val fiber = mock<SimFiber>()

        // And a sender and receiver that will be returned by the fiber
        whenever(fiber.lookUpResponderClass(receiverX500, "ping-ack"))
            .thenReturn(IckResponderFlow::class.java)

        // And an injector that will inject services
        val injector = mock<FlowServicesInjector>()

        // When we initiate the flow
        val flowMessaging = ConcurrentFlowMessaging(senderX500, PingAckFlow::class.java, fiber, injector, flowFactory)
        val sendingSession = flowMessaging.initiateFlow(receiverX500)

        // Then it should have injected the services into the responder
        verify(injector, times(1)).injectServices(responderFlow, receiverX500, fiber, flowFactory)

        // When we send and receive the message
        thread { sendingSession.send(message) }
        val received = sendingSession.receive(PingAckMessage::class.java).unwrap { it }

        // Then it should come through OK
        assertThat(received, `is`(PingAckMessage("Ick")))
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
            senderX500, PingAckFlow::class.java, flowAndServiceLookUp, injector, flowFactory)
        val sendingSession = flowMessaging.initiateFlow(receiverX500)

        // Then it should have injected the services into the responder
        verify(injector, times(1)).injectServices(responderFlow, receiverX500, flowAndServiceLookUp, flowFactory)

        // When we send and receive the message
        thread { sendingSession.send(message) }
        val received = sendingSession.receive(PingAckMessage::class.java).unwrap { it }

        // Then it should come through OK
        assertThat(received, `is`(PingAckMessage("Ick")))
    }

    class IckResponderFlow : ResponderFlow {
        override fun call(session: FlowSession) {
            session.send(PingAckMessage("Ick"))
        }

    }

}