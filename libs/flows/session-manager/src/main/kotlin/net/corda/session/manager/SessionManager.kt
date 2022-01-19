package net.corda.session.manager

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.messaging.api.records.Record
import java.time.Instant

/**
 * Session Manager offers methods to interact with and update the SessionState.
 * When session events are received they are deduplicated, reordered and validated.
 * Events received from counterparties are buffered within the state via [processMessage].
 * Events to be sent to counterparties originate from the current party's flow will update the sentMessages section of the [sessionState].
 * A status is tracked for the session based on what events have been sent/received.
 * Client library can get the next available event received via [getNextReceivedEvent].
 * SessionError/SessionAck influence the session state but are not passed to the client Llib.
 * Client library must mark events received as consumed via [acknowledgeReceivedEvent].
 */
interface SessionManager {

    /**
     * Process a session [event], and output the updated session state and an output record.
     * Session Event may originate from a counterparty or from this party's flow.
     * Events are deduplicated and reordered based on sequence number and stored within the session state.
     * [sessionState] tracks which events have been delivered to the client library as well as the next expected session event sequence
     * number to be received.
     * [flowKey] is provided for logging purposes.
     */
    //TODO possibly try split this into two for messages to be sent/messages received either here or further down
    fun processMessage(flowKey: FlowKey, sessionState: SessionState?, event: SessionEvent, instant: Instant): SessionEventResult

    /**
     * Get and return the next available buffered event in the correct sequence from the [sessionState] received from a counterparty.
     */
    fun getNextReceivedEvent(sessionState: SessionState?): SessionEvent?

    /**
     * Mark the session event with the sequence number equal to [seqNum] as consumed in the session state.
     * Event will be removed from undelivered events in received  events state
     */
    fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState

    /**
     * Generate a session error event for the [sessionId]. Set the error enveloper with the [errorMessage], [errorType] and [instant]
     */
    fun generateSessionErrorEvent(sessionId: String,
                                  errorMessage: String,
                                  errorType: String,
                                  instant: Instant
    ): Record<String, FlowMapperEvent>
}
