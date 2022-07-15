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
import net.cordapp.testing.chatframework.rpcRequestGenerator
import net.cordapp.testing.chatframework.getMockService
import net.cordapp.testing.chatframework.returnOnFind
import net.cordapp.testing.chatframework.withVirtualNodeName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import java.util.*

class ChatFlowCollaborationTests {
    companion object {
        val RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB"
        val FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB"
        val MESSAGE = "chat message"
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
    @Disabled
    fun `flow sends message to correct recipient`() {
        val messageLink = FlowMockMessageLink(from = outgoingFlowMockHelper, to = incomingFlowMockHelper).apply {
            addExpectedMessageType<MessageContainer>()
        }

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
                    id = UUID.randomUUID(), sender = FROM_X500_NAME, message = MESSAGE, timestamp = ""
                )
            )
        )

        // Check the message was removed after being read
        verify(readerFlowMockHelper.getMockService<PersistenceService>()).remove(expectedMessages.messages[0])
    }

    @Test
    @Disabled
    fun `flow sends message to correct recipient, message already pending for this sender`() {
        val messageLink = FlowMockMessageLink(from = outgoingFlowMockHelper, to = incomingFlowMockHelper).apply {
            addExpectedMessageType<MessageContainer>()
        }

        // Simulate that there's already a stored message for this sender
        val previouslyStoredMessage =
            IncomingChatMessage(
                id = UUID.randomUUID(),
                sender = FROM_X500_NAME,
                message = "an older message",
                timestamp = ""
            )
        incomingFlowMockHelper.returnOnFind(
            findKey = previouslyStoredMessage.sender,
            result = previouslyStoredMessage
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
                    id = UUID.randomUUID(), sender = FROM_X500_NAME, message = MESSAGE, timestamp = ""
                )
            )
        )

        // Check the message was removed after being read
        verify(readerFlowMockHelper.getMockService<PersistenceService>()).remove(expectedMessages.messages[0])
    }
}
