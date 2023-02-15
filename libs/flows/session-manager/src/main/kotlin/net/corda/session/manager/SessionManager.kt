package net.corda.session.manager

import java.time.Instant
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig

/**
 * Session Manager offers methods to interact with and update the [SessionState].
 * When [SessionEvent]s are received they are deduplicated, reordered and validated.
 * Events received from counterparties are buffered within the state via [processMessage].
 * Events to be sent to counterparties originate from the current party's flow. These events will be populated with the sentMessages section
 * of the [sessionState]. Messages to be sent to counterparties can be retrieved via [getMessagesToSend].
 * A status is tracked for the session based on what events have been sent/received.
 * Client library can get the next available event received via [getNextReceivedEvent].
 * [SessionError]/[SessionAck] events influence the session state but are not passed to the client lib.
 * Client library must mark events received as consumed via [acknowledgeReceivedEvent].
 */
interface SessionManager {

    /**
     * Process a session [event] received from a counterparty, and output the updated session state.
     * Any output messages to send are added to the undelivered sendEvents queue in the updated [sessionState].
     * These can be retrieved via [getMessagesToSend]
     * Events are deduplicated and reordered based on sequence number and stored within the session state.
     * [sessionState] tracks which events have been delivered to the client library as well as the next expected session event sequence
     * number to be received. [SessionState] should be set to null for [SessionInit] session events.
     * Any session acknowledgements available on the [SessionEvent] are used to remove messages from the undelivered sendEvents queue
     * @param key The key on which the [sessionState] is stored for logging purposes.
     * @param sessionState The session state. This should be null in the case of [SessionInit]
     * @param event Session event to process.
     * @param instant Timestamp to be applied for any output messages.
     * @return Updated session state with any output messages added to the undelivered sent events queue
     * and any valid received messages added to the undelivered received events queue
     */
    fun processMessageReceived(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant): SessionState

    /**
     * Process a session [event] to be sent to a counterparty, and output the updated session state.
     * in the updated [sessionState].
     * These can be retrieved via [getMessagesToSend]
     * [sessionState] tracks which events have been acknowledged by the counterparty as well as the next expected session event sequence
     * number to be sent. [SessionState] should be set to null for [SessionInit] session events.
     * [key] is provided for logging purposes.
     * @param key The key on which the [sessionState] is stored for logging purposes.
     * @param sessionState The session state. This should be null in the case of [SessionInit]
     * @param event Session event to process.
     * @param instant Timestamp to be applied for any output messages.
     * @param maxMsgSize Max size of messages to send
     * @return Updated session state with any output messages added to the undelivered sentEvents queue
     */
    fun processMessageToSend(key: Any, sessionState: SessionState?, event: SessionEvent, instant: Instant, maxMsgSize: Long): SessionState

    /**
     * Get and return the next available buffered event in the correct sequence from the [sessionState] received from a counterparty.
     * @param sessionState The session state.
     * @return The next session event to be processed. Return null when the next session event in the correct sequence is not available.
     */
    fun getNextReceivedEvent(sessionState: SessionState): SessionEvent?

    /**
     * Mark the session event with the sequence number equal to [seqNum] as consumed in the [sessionState].
     * The session event will be removed from undelivered events in the received events state.
     * @param sessionState The session state.
     * @param seqNum The sequence number to event to mark as consumed.
     * @return The updated session state.
     */
    fun acknowledgeReceivedEvent(sessionState: SessionState, seqNum: Int): SessionState

    /**
     * Get any messages to send to a peer from the [sessionState].
     * All messages with a timestamp in the past will be returned.
     * All messages of type SessionAck will be returned regardless of timestamp.
     * SessionAcks are also removed from the undelivered sendEvents state.
     * Triggers heartbeat messages at regular intervals.
     * If no response from counterparty after a configurable timeouts, session will move to error state.
     * @param sessionState The session state.
     * @param instant The time to check session events against when determining which messages need to be sent
     * @param config The config containing the flow session config values such as the resend time window
     * @param identity Identity of the calling party who owns the session state
     * @return The updated [SessionState] with SessionAcks removed as well as any messages to send to the counterparty.
     */
    fun getMessagesToSend(sessionState: SessionState, instant: Instant, config: SmartConfig, identity: HoldingIdentity): Pair<SessionState,
            List<SessionEvent>>
}
