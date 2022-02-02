package net.corda.session.manager.integration

import net.corda.data.flow.event.SessionEvent

interface BusInteractions {

    /**
     * Get the next message on the bus
     */
    fun getNextInboundMessage() : SessionEvent?

    /**
     * Get a specific session event from the bus by [seqNum]
     */
    fun getInboundMessage(seqNum: Int) : SessionEvent?

    /**
     * Get a specific session ack from the bus by [seqNum]
     */
    fun getInboundAck(seqNum: Int) : SessionEvent?

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
    fun receiveMessages(sessionEvents: List<SessionEvent>)
}