package net.corda.session.manager.integration

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.libs.configuration.SmartConfig
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.integration.helper.generateClose
import net.corda.session.manager.integration.helper.generateData
import net.corda.session.manager.integration.helper.generateError
import net.corda.session.manager.integration.helper.generateInit
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
        val sessionEvent = generateMessage(messageType)
        sessionState = sessionManager.processMessageToSend("key", sessionState, sessionEvent, instant)

        if (sendMessages) {
            sendMessages()
        }
    }

    override fun sendMessages(instant: Instant) {
        val (updatedState, outputMessages) = sessionManager.getMessagesToSend(sessionState!!, instant, testConfig)
        sessionState = updatedState
        outboundMessages.addMessages(outputMessages)
    }

    private fun generateMessage(messageType: SessionMessageType) : SessionEvent {
        return when(messageType) {
            SessionMessageType.INIT -> generateInit()
            SessionMessageType.DATA -> generateData()
            SessionMessageType.ERROR -> generateError()
            SessionMessageType.CLOSE -> generateClose()
        }
    }

    override fun processNextReceivedMessage(sendMessages: Boolean) {
        processAndAcknowledgeEventsInSequence(getNextInboundMessage())

        if (sendMessages) {
            sendMessages()
        }
    }

    override fun processAllReceivedMessages(sendMessages: Boolean) {
        var nextMessage = getNextInboundMessage()
        while (nextMessage != null) {
            processAndAcknowledgeEventsInSequence(nextMessage)
            nextMessage = getNextInboundMessage()
        }

        if (sendMessages) {
            sendMessages()
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