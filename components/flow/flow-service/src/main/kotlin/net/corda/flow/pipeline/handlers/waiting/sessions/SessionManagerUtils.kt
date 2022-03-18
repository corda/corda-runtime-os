package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.state.FlowCheckpoint
import net.corda.session.manager.SessionManager

fun SessionManager.getReceivedEvents(checkpoint: FlowCheckpoint, sessionIds: List<String>): List<Pair<SessionState, SessionEvent>> {
    return sessionIds.mapNotNull { sessionId ->
        val sessionState = checkpoint.getSessionState(sessionId)
            ?: throw FlowProcessingException("Session doesn't exist")
        getNextReceivedEvent(sessionState)?.let { sessionState to it }
    }
}

fun SessionManager.acknowledgeReceivedEvents(eventsToAcknowledge: List<Pair<SessionState, SessionEvent>>) {
    for ((sessionState, eventToAcknowledgeProcessingOf) in eventsToAcknowledge) {
        acknowledgeReceivedEvent(sessionState, eventToAcknowledgeProcessingOf.sequenceNum)
    }
}