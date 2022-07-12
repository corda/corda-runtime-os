package net.cordapp.testing.chat

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.cordapp.testing.chatframework.FlowMockHelper
import net.cordapp.testing.chatframework.FlowMockMessageLink
import net.cordapp.testing.chatframework.addExpectedMessageType
import net.cordapp.testing.chatframework.createFlow
import net.cordapp.testing.chatframework.createMockService
import net.cordapp.testing.chatframework.expectMergeAndLinkToFind
import net.cordapp.testing.chatframework.expectPersistAndLinkToFind
import net.cordapp.testing.chatframework.rpcRequestGenerator
import net.cordapp.testing.chatframework.getMockService
import net.cordapp.testing.chatframework.returnOnFind
import net.cordapp.testing.chatframework.withVirtualNodeName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChatFlowCollaborationTests {
    companion object {
        val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
        val FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
        val MESSAGE = "chat message"

        val DUMMY_FLOW_RETURN = "dummy_flow_return"
    }

    val outgoingFlowMockHelper = FlowMockHelper {
        createMockService<FlowMessaging>()
        createMockService<FlowEngine>().withVirtualNodeName(FROM_X500_NAME)
        createMockService<JsonMarshallingService>()
    }

    val outgoingChatFlow = outgoingFlowMockHelper.createFlow<ChatOutgoingFlow>()

    val incomingFlowMockHelper = FlowMockHelper {
        createMockService<FlowEngine>().withVirtualNodeName(RECIPIENT_X500_NAME)
        createMockService<PersistenceService>()
    }

    val incomingChatFlow = incomingFlowMockHelper.createFlow<ChatIncomingFlow>()

    val readerFlowMockHelper = FlowMockHelper {
        createMockService<FlowEngine>().withVirtualNodeName(RECIPIENT_X500_NAME)
        createMockService<JsonMarshallingService>()
        createMockService<PersistenceService>()
    }

    val readerChatFlow = readerFlowMockHelper.createFlow<ChatReaderFlow>()

    @Test
    fun `flow sends message to correct recipient`() {
        val messageLink = FlowMockMessageLink(from = outgoingFlowMockHelper, to = incomingFlowMockHelper).apply {
            addExpectedMessageType<MessageContainer>()
        }

        // Simulate that there's not already a stored message for this sender
        val previouslyStoredMessage = IncomingChatMessage(sender = FROM_X500_NAME, message = "")
        incomingFlowMockHelper.returnOnFind(
            findKey = previouslyStoredMessage.sender,
            result = null
        )

        // When a persist occurs in the incoming Flow, link the results of the persist to a find in the reader Flow
        incomingFlowMockHelper.expectPersistAndLinkToFind<IncomingChatMessage>(
            helperForFlowCallingFind = readerFlowMockHelper,
            findKey = previouslyStoredMessage.sender
        )

        executeConcurrently({
            outgoingChatFlow.call(
                outgoingFlowMockHelper.rpcRequestGenerator(
                    ChatOutgoingFlowParameter(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
                )
            )
        }, {
            incomingChatFlow.call(messageLink.toFlowSession)
        })

        messageLink.failIfPendingMessages()

        val expectedMessages = ReceivedChatMessages(
            messages = listOf(
                IncomingChatMessage(
                    sender = FROM_X500_NAME, message = MESSAGE
                )
            )
        )

        whenever(readerFlowMockHelper.getMockService<JsonMarshallingService>().format(expectedMessages)).thenReturn(
            DUMMY_FLOW_RETURN
        )

        val messagesJson = readerChatFlow.call(
            readerFlowMockHelper.rpcRequestGenerator(
                ChatReaderFlowParameter(fromName = FROM_X500_NAME)
            )
        )
        assertThat(messagesJson).isEqualTo(DUMMY_FLOW_RETURN)

        // Check the message was removed after being read
        verify(readerFlowMockHelper.getMockService<PersistenceService>()).remove(expectedMessages.messages[0])
    }

    @Test
    fun `flow sends message to correct recipient, message already pending for this sender`() {
        val messageLink = FlowMockMessageLink(from = outgoingFlowMockHelper, to = incomingFlowMockHelper).apply {
            addExpectedMessageType<MessageContainer>()
        }

        // Simulate that there's already a stored message for this sender
        val previouslyStoredMessage = IncomingChatMessage(sender = FROM_X500_NAME, message = "an older message")
        incomingFlowMockHelper.returnOnFind(
            findKey = previouslyStoredMessage.sender,
            result = previouslyStoredMessage
        )

        // When a merge occurs in the incoming Flow, link the results of the merge to a find in the reader Flow
        incomingFlowMockHelper.expectMergeAndLinkToFind<IncomingChatMessage>(
            helperForFlowCallingFind = readerFlowMockHelper,
            findKey = previouslyStoredMessage.sender,
            keyExtractor = { mergeParam -> mergeParam.sender },
            mergeOperation = { mergeParam -> IncomingChatMessage(previouslyStoredMessage.sender, mergeParam.message) }
        )

        executeConcurrently({
            outgoingChatFlow.call(
                outgoingFlowMockHelper.rpcRequestGenerator(
                    ChatOutgoingFlowParameter(recipientX500Name = RECIPIENT_X500_NAME, message = MESSAGE)
                )
            )
        }, {
            incomingChatFlow.call(messageLink.toFlowSession)
        })

        messageLink.failIfPendingMessages()

        val expectedMessages = ReceivedChatMessages(
            messages = listOf(
                IncomingChatMessage(
                    sender = FROM_X500_NAME, message = MESSAGE
                )
            )
        )

        whenever(readerFlowMockHelper.getMockService<JsonMarshallingService>().format(expectedMessages)).thenReturn(
            DUMMY_FLOW_RETURN
        )

        val messagesJson = readerChatFlow.call(
            readerFlowMockHelper.rpcRequestGenerator(
                ChatReaderFlowParameter(fromName = FROM_X500_NAME)
            )
        )
        assertThat(messagesJson).isEqualTo(DUMMY_FLOW_RETURN)

        // Check the message was removed after being read
        verify(readerFlowMockHelper.getMockService<PersistenceService>()).remove(expectedMessages.messages[0])
    }
}
