package net.corda.session.manager.integration

import net.corda.data.flow.event.SessionEvent

interface BusInteractions {

    /**
     * Get the next message on the bus
     */
    fun getNextInboundMessage() : SessionEvent?

    /**
     * Get the number of messages currently on the inbound message bus
     */
    fun getInboundMessageSize() : Int

    /**
     * Get a specific session event from the bus by [position]
     */
    fun getInboundMessage(position: Int) : SessionEvent

    /**
     * Remove the next message from the inbound messages
     */
    fun dropNextInboundMessage()

    /**
     * Remove the message from the inbound messages at a certain [position]
     */
    fun dropInboundMessage(position: Int)

    /**
     * Drop all messages
     */
    fun dropAllInboundMessages()

    /**
     * Randomly shuffle the messages on the bus
     */
    fun randomShuffleInboundMessages()

    /**
     * Reverse the order of messages on the bus
     */
    fun reverseInboundMessages()

    /**
     * Receive a list of [sessionEvents] to be stored on the bus
     */
    fun addMessages(sessionEvents: List<SessionEvent>)
}