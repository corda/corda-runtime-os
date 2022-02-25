package net.corda.session.manager.integration

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.libs.configuration.SmartConfig
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.integration.helper.generateMessage
import java.time.Instant

/**
 * Helper class to encapsulate a party involved in a session and the message bus in which it sends and receives session events.
 */
class SessionParty (
    private val inboundMessages: MessageBus,
    private val outboundMessages: MessageBus,
    private val testConfig: SmartConfig,
    var sessionState: SessionState? = null
) : SessionInteractions, BusInteractions by inboundMessages {

    private val sessionManager = SessionManagerImpl()

    override fun processNewOutgoingMessage(messageType: SessionMessageType, sendMessages: Boolean, instant: Instant) {
        val sessionEvent = generateMessage(messageType, instant)
        sessionState = sessionManager.processMessageToSend("key", sessionState, sessionEvent, instant)

        if (sendMessages) {
            sendMessages(instant)
        }
    }

    override fun sendMessages(instant: Instant) {
        val (updatedState, outputMessages) = sessionManager.getMessagesToSend(sessionState!!, instant, testConfig)
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