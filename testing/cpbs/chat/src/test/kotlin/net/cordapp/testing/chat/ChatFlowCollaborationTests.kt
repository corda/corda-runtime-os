package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.cordapp.testing.chatframework.FlowMockHelper
import net.cordapp.testing.chatframework.FlowMockMessageLink
import net.cordapp.testing.chatframework.addExpectedMessageType
import net.cordapp.testing.chatframework.createFlow
import net.cordapp.testing.chatframework.mockService
import net.cordapp.testing.chatframework.rpcRequestGenerator
import net.cordapp.testing.chatframework.serviceMock
import net.cordapp.testing.chatframework.withVirtualNodeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class ChatFlowCollaborationTests {
    val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
    val FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
    val MESSAGE = "chat message"

    val outgoingFlowMockHelper = FlowMockHelper {
        mockService<FlowMessaging>()
        mockService<FlowEngine>().withVirtualNodeName(FROM_X500_NAME)
        mockService<JsonMarshallingService>()
    }

    val outgoingChatFlow = outgoingFlowMockHelper.createFlow<ChatOutgoingFlow>()

    val incomingFlowMockHelper = FlowMockHelper {
        mockService<FlowEngine>().withVirtualNodeName(RECIPIENT_X500_NAME)
    }

    val incomingChatFlow = incomingFlowMockHelper.createFlow<ChatIncomingFlow>()

    val readerFlowMockHelper = FlowMockHelper {
        mockService<FlowEngine>().withVirtualNodeName(RECIPIENT_X500_NAME)
        mockService<JsonMarshallingService>()
    }

    val readerChatFlow = readerFlowMockHelper.createFlow<ChatReaderFlow>()

    @Test
    fun `flow sends message to correct recipient`() {
        val messageLink = FlowMockMessageLink(from = outgoingFlowMockHelper, to = incomingFlowMockHelper).apply {
            addExpectedMessageType<MessageContainer>()
        }

        ExecuteConcurrently({
                outgoingChatFlow.call(
                    outgoingFlowMockHelper.rpcRequestGenerator(
                        OutgoingChatMessage(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
                    ))
            }, {
                incomingChatFlow.call(messageLink.toFlowSession)
            })

        messageLink.failIfPendingMessages()

        val DUMMY_FLOW_RETURN="dummy_flow_return"
        val expectedMessages = ReceivedChatMessages(messages = listOf(IncomingChatMessage(
            senderX500Name = FROM_X500_NAME,
            message = MESSAGE)))

        whenever(readerFlowMockHelper.serviceMock<JsonMarshallingService>().format(expectedMessages))
            .thenReturn(DUMMY_FLOW_RETURN)

        val messagesJson = readerChatFlow.call(readerFlowMockHelper.rpcRequestGenerator(Any())) // Parameter not used by Flow
        assertThat(messagesJson).isEqualTo(DUMMY_FLOW_RETURN)
    }
}
