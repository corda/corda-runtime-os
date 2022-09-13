package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.testflows.PingAckMessage
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue

class BlockingQueueFlowSessionTest {

    private val sender = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")
    private val receiver = MemberX500Name.parse("CN=IRunCorDappsToo, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should retrieve a response from the common queue and pass it back to the sender`() {
        // Given a session constructed with shared queues
        val fromInitiatorToResponder = LinkedBlockingQueue<Any>()
        val fromResponderToInitiator = LinkedBlockingQueue<Any>()

        val sendingSession = BlockingQueueFlowSession(
            receiver,
            fromInitiatorToResponder,
            fromResponderToInitiator
        )

        val receivingSession = BlockingQueueFlowSession(
            sender,
            fromResponderToInitiator,
            fromInitiatorToResponder
        )

        // When we send a message
        val payload = PingAckMessage("Ping")
        sendingSession.send(payload)

        // Then we should be able to return the response that appears in the queue
        assertThat(receivingSession.receive<PingAckMessage>().message, `is`("Ping"))
    }
}
