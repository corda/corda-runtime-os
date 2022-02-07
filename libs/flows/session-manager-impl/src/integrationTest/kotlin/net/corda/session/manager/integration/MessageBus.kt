package net.corda.session.manager.integration

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import java.util.*

class MessageBus : BusInteractions {

    private val inboundMessages: LinkedList<SessionEvent> = LinkedList<SessionEvent>()

    override fun getNextInboundMessage() : SessionEvent? {
        return inboundMessages.poll()
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