package net.corda.flow.pipeline.handlers.requests.helper

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint

fun getSessionsToError(checkpoint: FlowCheckpoint, sessionIds: List<String>, flowSessionManager: FlowSessionManager): List<String> {
    val erroredSessions =
        flowSessionManager.getSessionsWithStatus(checkpoint, sessionIds, SessionStateType.ERROR)
    val closedSessions =
        flowSessionManager.getSessionsWithStatus(checkpoint, sessionIds, SessionStateType.CLOSED)

    return sessionIds - (erroredSessions + closedSessions).map { it.sessionId }.toSet()
}