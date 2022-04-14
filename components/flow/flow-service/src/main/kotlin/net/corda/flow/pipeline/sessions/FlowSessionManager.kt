package net.corda.flow.pipeline.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionClose
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.state.FlowCheckpoint
import net.corda.session.manager.SessionManager
import net.corda.v5.base.types.MemberX500Name
import java.time.Instant

/**
 * [FlowSessionManager] encapsulates the logic of [SessionManager] with a specific focus on its usage within the flow event pipeline.
 */
interface FlowSessionManager {

    /**
     * Create a new [SessionState] and queue a [SessionInit] message to send.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionId The session id of the new [SessionState].
     * @param x500Name The [MemberX500Name] that the [SessionInit] is addressed to.
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return A new [SessionState] containing a [SessionInit] message to send.
     */
    fun sendInitMessage(checkpoint: FlowCheckpoint, sessionId: String, x500Name: MemberX500Name, instant: Instant): SessionState

    /**
     * Queue [SessionData] messages to send to the passed in sessions.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionToPayload A map of the sessions ids to the payload to send.
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return Updated [SessionState] containing [SessionData] messages to send.
     *
     * @throws FlowProcessingException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun sendDataMessages(checkpoint: FlowCheckpoint, sessionToPayload: Map<String, ByteArray>, instant: Instant): List<SessionState>

    /**
     * Queue [SessionClose] messages to send to the passed in sessions.
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The sessions ids to close.
     * @param instant The [Instant] used within the created [SessionEvent].
     *
     * @return Updated [SessionState] containing [SessionClose] messages to send.
     *
     * @throws FlowProcessingException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun sendCloseMessages(checkpoint: FlowCheckpoint, sessionIds: List<String>, instant: Instant): List<SessionState>

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
     * @throws FlowProcessingException If a session does not exist within the flow's [FlowCheckpoint].
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
     * @throws FlowProcessingException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun hasReceivedEvents(checkpoint: FlowCheckpoint, sessionIds: List<String>): Boolean

    /**
     * Are all the specified sessions have a [SessionStateType] contained in [statuses]?
     *
     * @param checkpoint The flow's [FlowCheckpoint].
     * @param sessionIds The session ids to check the status of.
     * @param statuses The acceptable statuses the sessions can be in.
     *
     * @return `true`, if all sessions have a status contained in [statuses], `false` otherwise.
     *
     * @throws FlowProcessingException If a session does not exist within the flow's [FlowCheckpoint].
     */
    fun areAllSessionsInStatuses(checkpoint: FlowCheckpoint, sessionIds: List<String>, statuses: List<SessionStateType>): Boolean
}