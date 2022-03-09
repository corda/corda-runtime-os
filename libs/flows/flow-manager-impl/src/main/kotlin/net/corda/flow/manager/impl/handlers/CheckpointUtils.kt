package net.corda.flow.manager.impl.handlers

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CheckpointUtils")

fun Checkpoint.getSession(sessionId: String): SessionState? {
    val sessions = sessions.filter { it.sessionId == sessionId }
    return when {
        sessions.size > 1 -> {
            val message = "Flow [${flowKey.flowId}] has multiple sessions associated to a single sessionId [$sessionId]"
            log.error(message)
            throw FlowProcessingException(message)
        }
        else -> sessions.singleOrNull()
    }
}

fun Checkpoint.addOrReplaceSession(sessionState: SessionState) {
    val updated = sessions?.toMutableList()
    updated?.removeAll { it.sessionId == sessionState.sessionId }
    updated?.add(sessionState)
    sessions = updated
}