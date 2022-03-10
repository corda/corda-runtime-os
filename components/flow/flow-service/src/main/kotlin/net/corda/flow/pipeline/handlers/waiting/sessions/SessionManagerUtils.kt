package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.session.manager.SessionManager

fun SessionManager.getReceivedEvents(checkpoint: Checkpoint, sessionIds: List<String>): List<Pair<SessionState, SessionEvent>> {
    return sessionIds.mapNotNull { sessionId ->
        val sessionState = checkpoint.sessions.singleOrNull { it.sessionId == sessionId }
            ?: throw FlowProcessingException("Session doesn't exist")
        getNextReceivedEvent(sessionState)?.let { sessionState to it }
    }
}

fun SessionManager.acknowledgeReceivedEvents(eventsToAcknowledge: List<Pair<SessionState, SessionEvent>>) {
    for ((sessionState, eventToAcknowledgeProcessingOf) in eventsToAcknowledge) {
        acknowledgeReceivedEvent(sessionState, eventToAcknowledgeProcessingOf.sequenceNum)
    }
}