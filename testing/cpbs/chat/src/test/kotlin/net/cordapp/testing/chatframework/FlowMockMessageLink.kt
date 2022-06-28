package net.cordapp.testing.chatframework

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.cordapp.testing.chat.validateProtocol
import org.junit.jupiter.api.fail
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Helper which generates mock FlowSessions between two Flows. The sessions are set up such that messages sent to the
 * 'to' Flow by the 'from' Flow are received as they would be in the real world, that is the 'to' Flow will block
 * waiting for a message, but the 'from' Flow will not block on sending.
 * This class also validates that the Flows are declared with the correct protocol declarations.
 *
 * It is important to call addExpectedMessageType<>() on an instance of a FlowMockMessageLink before using it, in
 * order that it can mock the sending and receiving of expected message types. See that function for more details.
 *
 * @param from The FlowMockHelper for the Flow which initiates communication
 * @param to The FlowMockHelper for the ResponderFlow which is initiated by communication
 */
class FlowMockMessageLink(val from: FlowMockHelper, val to: FlowMockHelper) {

    /**
     * The mock FlowSession of the 'to' Flow. This can be passed to ResponderFlow.call(...) invocations.
     */
    lateinit var toFlowSession:FlowSession

    lateinit var messageQueue: FlowTestMessageQueue
    private val fromFlow = from.flow ?: fail("The 'from' FlowMockHelper has not been used to create a Flow")
    private val toFlow = validatedToFlow()

    fun validatedToFlow(): ResponderFlow {
        val toFlow = to.flow ?: fail("The 'to' FlowMockHelper has not been used to create a Flow")
        if (toFlow !is ResponderFlow) {
            fail("The 'to' Flow is not a ResponderFlow")
        }
        return toFlow
    }

    init {
        validateProtocol(fromFlow, toFlow)
        generateMockFlowSessions()
    }

    private fun generateMockFlowSessions() {
        val fromName = from.serviceMock<FlowEngine>().virtualNodeName
        val toName = to.serviceMock<FlowEngine>().virtualNodeName

        this.toFlowSession = mock<FlowSession>().also {
            whenever(it.counterparty).thenReturn(fromName)
        }

        val fromFlowSession = from.expectFlowMessagesTo(toName)
        messageQueue = FlowTestMessageQueue(fromFlowSession, this.toFlowSession)
    }

    fun failIfPendingMessages() {
        messageQueue.failIfNotEmpty()
    }
}

/**
 * Each message type must be registered with FlowMockMessageLink such that an expectation can be set up for it.
 * If an expectation is not set up for a type and a 'receive' is attempted for that type, the 'receive' will
 * drop through with default Mockito behaviour. In this case your test will exhibit undefined behaviour as that
 * single 'receive' doesn't return the next thing off the queue but the Flow will continue to execute believing
 * something was received.
 */
inline fun <reified T : Any> FlowMockMessageLink.addExpectedMessageType() {
    messageQueue.addExpectedMessageType<T>()
}
