package net.corda.session.manager.integration

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.impl.factory.SessionEventProcessorFactory
import net.corda.session.manager.integration.helper.generateMessage
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Helper class to encapsulate a party involved in a session and the message bus in which it sends and receives session events.
 */
class SessionParty (
    private val inboundMessages: MessageBus,
    private val outboundMessages: MessageBus,
    private val testConfig: SmartConfig,
    var sessionState: SessionState? = null
) : SessionInteractions, BusInteractions by inboundMessages {

    private val messagingChunkFactory : MessagingChunkFactory = mock<MessagingChunkFactory>().apply {
        whenever(createChunkSerializerService(any())).thenReturn(mock())
    }
    private val sessionManager = SessionManagerImpl(SessionEventProcessorFactory(messagingChunkFactory), messagingChunkFactory)
    private val testIdentity = HoldingIdentity()
    private val maxMsgSize = 10000000L

    override fun processNewOutgoingMessage(messageType: SessionMessageType, sendMessages: Boolean, instant: Instant) {
        val sessionEvent = generateMessage(messageType, instant)
        sessionState = sessionManager.processMessageToSend("key", sessionState, sessionEvent, instant, maxMsgSize)

        if (sendMessages) {
            sendMessages(instant)
        }
    }

    override fun sendMessages(instant: Instant) {
        val (updatedState, outputMessages) = sessionManager.getMessagesToSend(sessionState!!, instant, testConfig, testIdentity)
        sessionState = updatedState
        outboundMessages.addMessages(outputMessages)
    }

    override fun processNextReceivedMessage(sendMessages: Boolean, instant: Instant) {
        processAndAcknowledgeEventsInSequence(getNextInboundMessage())

        if (sendMessages) {
            sendMessages(instant)
        }
    }

    override fun processAllReceivedMessages(sendMessages: Boolean, instant: Instant) {
        var nextMessage = getNextInboundMessage()
        while (nextMessage != null) {
            processAndAcknowledgeEventsInSequence(nextMessage)
            nextMessage = getNextInboundMessage()
        }

        if (sendMessages) {
            sendMessages(instant)
        }
    }

    private fun processAndAcknowledgeEventsInSequence(nextMessage: SessionEvent?) {
        if (nextMessage != null) {
            sessionState = sessionManager.processMessageReceived("key", sessionState, nextMessage, Instant.now())

            var message = sessionManager.getNextReceivedEvent(sessionState!!)
            while (message != null) {
                sessionState = sessionManager.acknowledgeReceivedEvent(sessionState!!, message.sequenceNum)
                message = sessionManager.getNextReceivedEvent(sessionState!!)
            }
        }
    }
}