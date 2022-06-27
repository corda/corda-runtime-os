package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class ChatFlowCollaborationTests {
    val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
    val FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
    val MESSAGE = "chat message"

    val outgoingInjector = FlowTestDependencyInjector {
        mockService<FlowMessaging>()
        mockService<FlowEngine>().withVirtualNodeName(FROM_X500_NAME)
        mockService<JsonMarshallingService>()
    }

    val outgoingChatFlow = ChatOutgoingFlow().also {
        outgoingInjector.injectServices(it)
    }

    val incomingInjector = FlowTestDependencyInjector {
        mockService<FlowEngine>().withVirtualNodeName(RECIPIENT_X500_NAME)
    }

    val incomingChatFlow = ChatIncomingFlow().also {
        incomingInjector.injectServices(it)
    }

    val readerInjector = FlowTestDependencyInjector {
        mockService<FlowEngine>().withVirtualNodeName(RECIPIENT_X500_NAME)
        mockService<JsonMarshallingService>()
    }

    val readerChatFlow = ChatReaderFlow().also {
        readerInjector.injectServices(it)
    }

    @Test
    fun `flow sends message to correct recipient`() {
        /**
         * Check the two Flows have the correct annotations to be part of the same protocol
         */
        validateProtocol(outgoingChatFlow, incomingChatFlow)


        val outgoingFlowSession = outgoingInjector.expectFlowMessagesTo(MemberX500Name.parse(RECIPIENT_X500_NAME))
        val incomingFlowSession = expectFlowMessagesFrom(MemberX500Name.parse(FROM_X500_NAME))

        val messageQueue = FlowTestMessageQueue(from = outgoingFlowSession, to = incomingFlowSession).apply {
            addExpectedMessageType<MessageContainer>()
        }

        ExecuteConcurrently({
                outgoingChatFlow.call(
                    outgoingInjector.rpcRequestGenerator(
                        OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
                    ))
            }, {
                incomingChatFlow.call(incomingFlowSession)
            })

        messageQueue.failIfNotEmpty()

        val DUMMY_FLOW_RETURN="dummy_flow_return"
        val expectedMessages = ReceivedChatMessages(messages = listOf(IncomingChatMessage(
            senderX500Name = FROM_X500_NAME,
            message = MESSAGE)))

        whenever(readerInjector.serviceMock<JsonMarshallingService>().format(expectedMessages))
            .thenReturn(DUMMY_FLOW_RETURN)

        val messagesJson = readerChatFlow.call(readerInjector.rpcRequestGenerator(Any())) // Parameter not used by Flow
        assertThat(messagesJson).isEqualTo(DUMMY_FLOW_RETURN)
    }
}
