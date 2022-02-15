package net.corda.session.manager.integration

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import java.util.*

class MessageBus : BusInteractions {

    private val inboundMessages: LinkedList<SessionEvent> = LinkedList<SessionEvent>()

    override fun getNextInboundMessage() : SessionEvent? {
        return inboundMessages.poll()
    }

    override fun duplicateMessage(position: Int) {
        //TODO - this works for now as we dont modify data but this will point to the same object
        val message = inboundMessages[position]
        inboundMessages.add(message)
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