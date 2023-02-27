package net.corda.simulator.runtime.flows

import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.messaging.BaseInitiatorFlowSession
import net.corda.simulator.runtime.messaging.FlowContext
import net.corda.simulator.runtime.messaging.SimFlowContextProperties
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class InitiatorFlowSessionTest {

    private val sender = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
    private val flowCallConfiguration = SimulatorConfigurationBuilder.create().build()

    @Test
    fun `initiators should throw an exception if receivedException is set`() {
        // Given a session constructed only on the sending side
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()
        val flowContextProperties = SimFlowContextProperties(emptyMap())

        val sendingSession = BaseInitiatorFlowSession(
            FlowContext(flowCallConfiguration, sender, "ping-ack"),
            fromResponderToInitiator,
            fromInitiatorToResponder,
            flowContextProperties
        )

        // When we send a message and then set a received exception
        thread {
            Thread.sleep(100)
            sendingSession.responderErrorCaught(IllegalArgumentException("Just because"))
        }

        // Then it should throw in that receive
        assertThrows<ResponderFlowException> {
            sendingSession.receive(Any::class.java)
        }

        // Or in any subsequent send
        assertThrows<ResponderFlowException> {
            sendingSession.send(Unit)
        }

        // Or any subsequent receive
        assertThrows<ResponderFlowException> {
            sendingSession.receive(Any::class.java)
        }

        // Or when we try to close the session
        assertThrows<ResponderFlowException> {
            sendingSession.close()
        }
    }
}