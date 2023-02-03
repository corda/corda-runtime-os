package net.corda.flow.pipeline.sessions

import java.time.Instant
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.session.manager.SessionManager
import net.corda.v5.base.types.MemberX500Name

/**
 * [FlowSessionManager] encapsulates the logic of [SessionManager] with a specific focus on its usage within the flow event pipeline.
 */
interface FlowSessionManager {

    /**
     * Get error event records for session errors.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param flowConfig The config containing the flow session config values such as the resend time window
     */
    fun getSessionErrorEventRecords(checkpoint: FlowCheckpoint, flowConfig: SmartConfig, instant: Instant): List<Record<*, FlowMapperEvent>>

    /**
     * Create a new [SessionState] and queue a [SessionInit] message to send.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionId The session id of the new [SessionState].
     * @param x500Name The [MemberX500Name] that the [SessionInit] is addressed to.
     * @param protocolName The name of the protocol to use in this session
     * @param protocolVersions The versions of the protocol supported by the initiating side
     * @param contextUserProperties The user context properties
     * @param contextPlatformProperties The platform context properties
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return A new [SessionState] containing a [SessionInit] message to send.
     */
    @Suppress("LongParameterList")
    fun sendInitMessage(
        checkpoint: FlowCheckpoint,
        sessionId: String,
        x500Name: MemberX500Name,
        protocolName: String,
        protocolVersions: List<Int>,
        contextUserProperties: KeyValuePairList,
        contextPlatformProperties: KeyValuePairList,
        instant: Instant
    ): SessionState

    /**
     * Queue [SessionData] messages to send to the passed in sessions.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionToPayload A map of the sessions ids to the payload to send.
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return Updated [SessionState] containing [SessionData] messages to send.
     *
     * @throws FlowSessionStateException If a session does not exist within the flow's [FlowCheckpoint], or is not in
     * the CONFIRMED state.
     */
    fun sendDataMessages(
        checkpoint: FlowCheckpoint,
        sessionToPayload: Map<String, ByteArray>,
        instant: Instant
    ): List<SessionState>

    /**
     * Queue [SessionClose] messages to send to the passed in sessions.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The sessions ids to close.
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return Updated [SessionState] containing [SessionClose] messages to send.
     *
     * @throws FlowSessionStateException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun sendCloseMessages(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        instant: Instant
    ): List<SessionState>

    /**
     * Queue [SessionError] messages to send to the passed in sessions.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The sessions ids to error.
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return Updated [SessionState] containing [SessionError] messages to send.
     *
     * @throws FlowSessionStateException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun sendErrorMessages(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        throwable: Throwable,
        instant: Instant
    ): List<SessionState>

    /**
     * Gets the next received session event for each passed in session id.
     *
     * Sessions that have not received session events are filtered out of the returned list.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The sessions ids to get session events for.
     *
     * @return A list of [SessionState]s to [SessionEvent]s. Sessions without session events are not contained in this list.
     *
     * @throws FlowSessionStateException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun getReceivedEvents(checkpoint: FlowCheckpoint, sessionIds: List<String>): List<Pair<SessionState, SessionEvent>>

    /**
     * Acknowledges the passed in session events.
     *
     * @param eventsToAcknowledge A list of [SessionEvent]s to acknowledge and their corresponding [SessionState]s to update.
     */
    fun acknowledgeReceivedEvents(eventsToAcknowledge: List<Pair<SessionState, SessionEvent>>)

    /**
     * Have specified sessions all received their next session events?
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The session ids to get session events for.
     *
     * @return `true`, if all sessions have received their next session event, `false` otherwise.
     *
     * @throws FlowSessionStateException If a session does not exist within the flow's [FlowCheckpoint], or is not in
     * the CONFIRMED or CLOSING state. Receiving events is tolerant to sessions which are closing but not yet closed.
     */
    fun hasReceivedEvents(checkpoint: FlowCheckpoint, sessionIds: List<String>): Boolean

    /**
     * Gets the sessions with the passed in [status].
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The session ids to check the status of.
     * @param status The acceptable status the sessions can have.
     *
     * @return A list of [SessionState]s that have a [SessionState.status] of [status].
     *
     * @throws []FlowSessionStateException] If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun getSessionsWithStatus(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>,
        status: SessionStateType
    ): List<SessionState>

    /**
     * Are all the specified sessions have a [SessionStateType] of [status]?
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The session ids to check the status of.
     * @param status The acceptable status the sessions can have.
     *
     * @return `true`, if all sessions have status [status], `false` otherwise.
     *
     * @throws [FlowSessionStateException] If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun doAllSessionsHaveStatus(checkpoint: FlowCheckpoint, sessionIds: List<String>, status: SessionStateType): Boolean

    /**
     * Get the states whose next ordered message is a SessionClose.
     * This allows for detection of states which have received an ordered close when it is not expected.
     * @param checkpoint The checkpoint to check states within
     * @param sessionIds The sessions to check
     * @return The list of states whose next received ordered message is a SessionClose
     */
    fun getSessionsWithNextMessageClose(
        checkpoint: FlowCheckpoint,
        sessionIds: List<String>
    ): List<SessionState>
}