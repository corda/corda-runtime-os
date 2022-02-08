package net.corda.session.manager.integration

import java.time.Instant

interface SessionInteractions {

    /**
     * Generate a new message of type [messageType].
     * The session manager will queue this message inside its internal SessionState.
     * Queued messages are sent via sendMessages() when [sendMessages] is set to true.
     */
    fun processNewOutgoingMessage(messageType: SessionMessageType, sendMessages: Boolean = false)

    /**
     * Send messages to the counterparty of the session.
     * Sends all new messages stored in the SessionState that have been queued.
     * Resend any messages which have not been acknowledged after the configured time window
     * comparing against the time provided via [instant]
     */
    fun sendMessages(instant: Instant = Instant.now())

    /**
     * Get the next message from the bus and process it with the session manager.
     * Set [sendMessages] to true to send any queued messages to the counterparty.
     * This call will also trigger the client lib to mark any contiguous messages received as delivered automatically.
     */
    fun processNextReceivedMessage(sendMessages: Boolean = false)

    /**
     * Get all messages from the bus and process them with the session manager.
     * Set [sendMessages] to true to send any queued messages to the counterparty.
     * This call will also trigger the client lib to mark any contiguous messages received events as delivered automatically.
     */
    fun processAllReceivedMessages(sendMessages: Boolean = false)
}
