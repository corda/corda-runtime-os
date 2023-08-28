package net.corda.flow.pipeline.handlers.requests.sessions.service

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
/**
 * Applies filtering where close has been called
 * filters according to protocol for when SessionClose can be sent
 * updates statuses and returns sessions that can be closed.
 */
@Component(service = [CloseSessionService::class])
class CloseSessionService @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) {
    /**
     * supplies a list of sessions that can be set as WaitingFor SessionClose
     * @param checkpoint - the checkpoint
     * @param sessions - list of sessions IDs
     * @return [List] of [String] of sessions IDs
     */
    fun getSessionsToCloseForWaitingFor(
        checkpoint: FlowCheckpoint,
        sessions: List<String>
    ): List<String> {

        //filter out initiated
        val initiatingAndInitiated = flowSessionManager.getInitiatingAndInitiatedSessions(sessions)
        val initiatingSessions = initiatingAndInitiated.first

        //filter out states
        val filteredSessions = filterOutErrorAndClosed(checkpoint, initiatingSessions)

        //filter out RequireClose false
        val requireCloseSessions = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, filteredSessions)

        return requireCloseSessions.first
    }

    /**
     * applies filtering and changes status using updateStatus api
     * @param checkpoint - the checkpoint
     * @param sessions - list of sessions IDs
     */
    fun handleCloseForSessions(sessions: List<String>, checkpoint: FlowCheckpoint) {
        //filter initiating and initiated
        val initiatingAndInitiated = flowSessionManager.getInitiatingAndInitiatedSessions(sessions)
        val initiatingSessions = initiatingAndInitiated.first
        val initiatedSessions = initiatingAndInitiated.second

        if (initiatedSessions.isNotEmpty()) {
            checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, initiatedSessions, Instant.now()))
        }

        if (initiatingSessions.isNotEmpty()) {
            //filter out states
            //status == ERROR/CLOSED-> do nothing, sessions are already terminated
            val filteredSessions = filterOutErrorAndClosed(checkpoint, initiatingSessions)

            //filter RequireClose
            val requireCloseTrueAndFalse = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, filteredSessions)
            val requireCloseTrue = requireCloseTrueAndFalse.first
            val requireCloseFalse = requireCloseTrueAndFalse.second

            if (requireCloseTrue.isNotEmpty()) {
                //requireClose == true && status == CLOSING -> go to CLOSED
                closeSessionsAlreadyInClosing(checkpoint, requireCloseTrue)
                //requireClose == true && status == CREATED/CONFIRMED -> go to CLOSING
                val statuses = setOf(SessionStateType.CONFIRMED, SessionStateType.CREATED)
                putSessionsInClosing(checkpoint, requireCloseTrue, statuses)
            }
            //requireClose == false -> go to CLOSED.
            if (requireCloseFalse.isNotEmpty()) {
                flowSessionManager.updateStatus(checkpoint, requireCloseFalse, SessionStateType.CLOSED)
            }
        }
    }

    /**
     * put a session status in CLOSING
     * @param checkpoint - the checkpoint
     * @param sessions - list of sessions IDs
     * @param statuses - set of statuses to put in CLOSING
     */
    private fun putSessionsInClosing(checkpoint: FlowCheckpoint, sessions: List<String>, statuses: Set<SessionStateType>) {
        val statesToClose = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statuses)
        val sessionsToClose = statesToClose.map { it.sessionId }
        flowSessionManager.updateStatus(checkpoint, sessionsToClose, SessionStateType.CLOSING)
    }

    /**
     * put a session status from CLOSING to CLOSED
     * @param checkpoint - the checkpoint
     * @param sessions - list of sessions IDs
     */
    private fun closeSessionsAlreadyInClosing(checkpoint: FlowCheckpoint, sessions: List<String>) {
        val statesAlreadyInClosing = flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.CLOSING)
        val sessionsAlreadyInClosing = statesAlreadyInClosing.map { it.sessionId }
        flowSessionManager.updateStatus(checkpoint, sessionsAlreadyInClosing, SessionStateType.CLOSED)
    }

    /**
     * filter out sessions where their status is ERROR or CLOSED
     * @param checkpoint - the checkpoint
     * @param sessions - list of sessions IDs
     * @return [List] of [String] sessions that have had ERROR or CLOSED sessions removed
     */
    private fun filterOutErrorAndClosed(checkpoint: FlowCheckpoint, sessions: List<String>): List<String> {
        val statusToFilterOut = setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
        val statesInErrorOrClosed = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusToFilterOut)
        val sessionsInErrorOrClosed = statesInErrorOrClosed.map { it.sessionId }
        return sessions - sessionsInErrorOrClosed
    }
}