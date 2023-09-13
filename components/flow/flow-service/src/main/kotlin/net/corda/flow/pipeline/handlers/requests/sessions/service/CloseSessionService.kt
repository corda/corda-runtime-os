package net.corda.flow.pipeline.handlers.requests.sessions.service

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [CloseSessionService::class])
class CloseSessionService @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) {
    /**
     * Takes a list of [sessions] to evaluate.
     * Checks whether the flow needs to wait for any of the sessions to terminate.
     * Returns a list of sessions not yet terminated.
     * @param checkpoint The checkpoint.
     * @param sessions List of sessions IDs to evaluate.
     * @return Sessions not yet terminated
     */
    fun getSessionsToCloseForWaitingFor(
        checkpoint: FlowCheckpoint,
        sessions: List<String>
    ): List<String> {
        val (initiatingSessions, _) = flowSessionManager.getInitiatingAndInitiatedSessions(sessions)
        val (requireCloseTrueSessions, _) = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, initiatingSessions)
        return requireCloseTrueSessions
    }

    /**
     * Executes the close logic for the given [sessions].
     * @param checkpoint The checkpoint.
     * @param sessions List of sessions IDs.
     * @return List of sessions that are not CLOSED or ERROR
     */
    fun handleCloseForSessions(checkpoint: FlowCheckpoint, sessions: List<String>) {
        val (initiatingSessions, initiatedSessions) = flowSessionManager.getInitiatingAndInitiatedSessions(sessions)
        if (initiatedSessions.isNotEmpty()) {
            checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, initiatedSessions, Instant.now()))
        }
        if (initiatingSessions.isNotEmpty()) {
            processInitiatingSessions(checkpoint, initiatingSessions)
        }
    }

    private fun processInitiatingSessions(
        checkpoint: FlowCheckpoint,
        initiatingSessions: List<String>
    ) {
        val (requireCloseTrue, requireCloseFalse) = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, initiatingSessions)
        if (requireCloseTrue.isNotEmpty()) {
            closeSessionsAlreadyInClosing(checkpoint, requireCloseTrue)
            putSessionsInClosing(checkpoint, requireCloseTrue, setOf(SessionStateType.CONFIRMED, SessionStateType.CREATED))
        }

        if (requireCloseFalse.isNotEmpty()) {
            checkpoint.putSessionStates(flowSessionManager.updateStatus(checkpoint, requireCloseFalse, SessionStateType.CLOSED))
        }
    }

    /**
     * Put a session status in CLOSING if the session states status are in the given set of [statuses].
     * Sessions in states ERROR/CLOSED are already terminated and do not need to be updated.
     * @param checkpoint The checkpoint.
     * @param sessions List of sessions to update.
     * @param statuses sSet of statuses to put in CLOSING
     */
    private fun putSessionsInClosing(checkpoint: FlowCheckpoint, sessions: List<String>, statuses: Set<SessionStateType>) {
        val statesToClose = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statuses)
        val sessionsToClose = statesToClose.map { it.sessionId }
        checkpoint.putSessionStates(flowSessionManager.updateStatus(checkpoint, sessionsToClose, SessionStateType.CLOSING))
    }

    /**
     * Put a session status from CLOSING to CLOSED.
     * If status is CLOSING then a close message has already been received.
     * @param checkpoint The checkpoint.
     * @param sessions List of sessions IDs.
     */
    private fun closeSessionsAlreadyInClosing(checkpoint: FlowCheckpoint, sessions: List<String>) {
        val statesAlreadyInClosing = flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.CLOSING)
        val sessionsAlreadyInClosing = statesAlreadyInClosing.map { it.sessionId }
        checkpoint.putSessionStates(flowSessionManager.updateStatus(checkpoint, sessionsAlreadyInClosing, SessionStateType.CLOSED))
    }
}
