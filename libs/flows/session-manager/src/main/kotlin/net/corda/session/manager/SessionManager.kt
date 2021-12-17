package net.corda.session.manager

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.messaging.api.records.Record
import java.time.Clock

/**
 * Session Manager offers methods to interact with and update the SessionState.
 * When session events are received they are deduplicated, reordered and validated.
 * Events received are buffered within the state via [processReceivedMessage].
 * A status is tracked for the session based on what events have been sent/received.
 * Client library can get the next available event via [getNextReceivedEvent].
 * SessionError/SessionAck influence the session state but are not passed to the client Llib.
 * Client library must mark events received as consumed via [acknowledgeReceivedEvent].
 */
interface SessionManager {

    /**
     * Receive a session [event], process it and output the updated session state and an output record (ack or error).
     * Events are deduplicated and reordered based on sequence number and stored within the session state.
     * [sessionState] tracks which events have been delivered to the client library as well as the next expected session event sequence
     * number to be received.
     * [flowKey] is provided for logging purposes.
     */
    fun processReceivedMessage(flowKey: FlowKey, sessionState: SessionState?, event: SessionEvent): SessionEventResult

    /**
     * Get and return the next available buffered event in the correct sequence from the [sessionState].
     */
    fun getNextReceivedEvent(sessionState: SessionState?): SessionEvent?

    /**
     * Mark the session event with the sequence number equal to [seqNum] as consumed in the session state.
     * Event will be removed from undelivered events in received  events state
     */
    fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState

    /**
     * Generate a session error event for the [sessionId]. Set the error enveloper with the [errorMessage], [errorType] and [clock]
     */
    fun generateSessionErrorEvent(sessionId: String,
                                  errorMessage: String,
                                  errorType: String,
                                  clock: Clock): Record<String, FlowMapperEvent>
}
