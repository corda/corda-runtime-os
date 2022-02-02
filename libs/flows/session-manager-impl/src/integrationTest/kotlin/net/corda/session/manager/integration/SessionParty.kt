package net.corda.session.manager.integration

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.state.session.SessionState
import net.corda.session.manager.impl.SessionManagerImpl
import java.time.Instant
import java.util.*

class SessionParty (
    var otherParty: SessionParty? = null,
    var sessionState: SessionState? = null,
    var inboundMessages: LinkedList<SessionEvent> = LinkedList<SessionEvent>()
) : SessionInteractions, BusInteractions {

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
        otherParty?.receiveMessages(outputMessages)
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

    override fun processReceivedMessage(seqNum: Int, sendMessages: Boolean, isAck: Boolean) {
        val nextMessage = if (isAck) getInboundAck(seqNum) else getInboundMessage(seqNum)
        processAndAcknowledgeEventsInSequence(nextMessage)

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

    override fun receiveMessages(sessionEvents: List<SessionEvent>) {
        sessionEvents.map { it.messageDirection = MessageDirection.INBOUND }
        inboundMessages.addAll(sessionEvents)
    }

    override fun getNextInboundMessage() : SessionEvent? {
        return inboundMessages.poll()
    }

    override fun getInboundMessage(seqNum: Int) : SessionEvent? {
        val message = inboundMessages.find { it.sequenceNum == seqNum }
        inboundMessages.remove(message)
        return message
    }

    override fun getInboundAck(seqNum: Int) : SessionEvent? {
        val ack = inboundMessages.find { it.payload::class.java == SessionAck::class.java
                && (it.payload as SessionAck).sequenceNum == seqNum }
        inboundMessages.remove(ack)
        return ack
    }

    override fun randomShuffleInboundMessages() {
        inboundMessages.shuffle()
    }

    override fun reverseInboundMessages() {
        inboundMessages.reverse()
    }
}