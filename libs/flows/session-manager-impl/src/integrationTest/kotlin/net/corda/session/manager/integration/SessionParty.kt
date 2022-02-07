package net.corda.session.manager.integration

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionManagerImpl
import net.corda.session.manager.integration.helper.generateClose
import net.corda.session.manager.integration.helper.generateData
import net.corda.session.manager.integration.helper.generateError
import net.corda.session.manager.integration.helper.generateInit
import java.time.Instant

class SessionParty (
    private val inboundMessages: MessageBus,
    private val outboundMessages: MessageBus,
    var sessionState: SessionState? = null
) : SessionInteractions, BusInteractions by inboundMessages {

    private val sessionManager = SessionManagerImpl()

    override fun processNewOutgoingMessage(messageType: SessionMessageType, sendMessages: Boolean) {
        val sessionEvent = generateMessage(messageType)
        sessionState = sessionManager.processMessageToSend("key", sessionState, sessionEvent, Instant.now())

        if (sendMessages) {
            sendMessages()
        }
    }

    override fun sendMessages() {
        val (updatedState, outputMessages) = sessionManager.getMessagesToSend(sessionState!!)
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