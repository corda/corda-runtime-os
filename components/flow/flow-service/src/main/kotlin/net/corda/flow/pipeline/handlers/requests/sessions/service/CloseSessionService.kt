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

    fun getSessionsForPostProcess(sessionsToClose: List<String>, checkpoint: FlowCheckpoint) {

        val initiatingAndInitiated = flowSessionManager.getInitiatingAndInitiatedSessions(sessionsToClose)
        val initiatingSessions = initiatingAndInitiated.first
        val initiatedSessions = initiatingAndInitiated.second

        if (initiatedSessions.isNotEmpty()) {
            checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, initiatedSessions, Instant.now()))
        }

        if (initiatingSessions.isNotEmpty()) {
            closeSessionsAlreadyInClosing(checkpoint, initiatingSessions)

            val requireCloseTrueAndFalse = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, initiatingSessions)
            val requireCloseTrue = requireCloseTrueAndFalse.first
            val requireCloseFalse = requireCloseTrueAndFalse.second

            if (requireCloseFalse.isNotEmpty()) {
                flowSessionManager.updateStatus(checkpoint, requireCloseFalse, SessionStateType.CLOSED)
            }

            if (requireCloseTrue.isNotEmpty()) {
                flowSessionManager.updateStatus(checkpoint, requireCloseFalse, SessionStateType.CLOSING)
            }
        }
    }

    private fun closeSessionsAlreadyInClosing(checkpoint: FlowCheckpoint, sessions: List<String>) {
        val statesAlreadyInClosing = flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.CLOSING)
        val sessionsAlreadyInClosing = statesAlreadyInClosing.map { it.sessionId }
        flowSessionManager.updateStatus(checkpoint, sessionsAlreadyInClosing, SessionStateType.CLOSED)
    }

    private fun filterOutErrorAndClosed(checkpoint: FlowCheckpoint, sessions: List<String>): List<String> {
        val statusToFilterOut = setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
        val statesInErrorOrClosed = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusToFilterOut)
        val sessionsInErrorOrClosed = statesInErrorOrClosed.map { it.sessionId }
        return sessions - sessionsInErrorOrClosed
    }
}