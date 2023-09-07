package net.corda.session.manager.integration

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.flow.utils.INITIATED_SESSION_ID_SUFFIX
import net.corda.flow.utils.isInitiatedIdentity
import java.util.LinkedList

class MessageBus : BusInteractions {

    private val inboundMessages: LinkedList<SessionEvent> = LinkedList<SessionEvent>()

    override fun getNextInboundMessage(isInitiating: Boolean) : SessionEvent? {
        return toggleSessionId(inboundMessages.poll())
    }

    private fun toggleSessionId(sessionEvent: SessionEvent?): SessionEvent? {
        val sessionId = sessionEvent?.sessionId ?: return sessionEvent
        sessionEvent.sessionId = if (isInitiatedIdentity(sessionId)) {
            sessionId.removeSuffix(INITIATED_SESSION_ID_SUFFIX)
        } else {
            sessionId + INITIATED_SESSION_ID_SUFFIX
        }
        return sessionEvent
    }

    override fun duplicateMessage(position: Int) {
        val message = inboundMessages[position]
        val copy = message.specificData.deepCopy(message.schema, message)
        inboundMessages.add(copy)
    }

    override fun getInboundMessageSize(): Int {
        return inboundMessages.size
    }

    override fun getInboundMessage(position: Int) : SessionEvent {
        return inboundMessages.removeAt(position)
    }

    override fun dropNextInboundMessage() {
        inboundMessages.poll()
    }

    override fun dropInboundMessage(position: Int) {
        inboundMessages.removeAt(position)
    }

    override fun dropAllInboundMessages() {
        inboundMessages.clear()
    }

    override fun randomShuffleInboundMessages() {
        inboundMessages.shuffle()
    }

    override fun reverseInboundMessages() {
        inboundMessages.reverse()
    }

    override fun addMessages(sessionEvents: List<SessionEvent>) {
        sessionEvents.map { it.messageDirection = MessageDirection.INBOUND }
        inboundMessages.addAll(sessionEvents)
    }
}