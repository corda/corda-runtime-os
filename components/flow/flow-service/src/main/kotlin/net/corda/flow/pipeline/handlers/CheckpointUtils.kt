package net.corda.flow.pipeline.handlers

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.session.manager.Constants.Companion.INITIATED_SESSION_ID_SUFFIX
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

/**
 * Get the initiating and initiated identities.
 * @param sessionState Session state
 * @param checkpointIdentity The identity of the party running this flow
 * @return Pair with initiating party as the first identity, initiated party as the second identity
 */
@Suppress("Unused")
fun getInitiatingAndInitiatedParties(sessionState: SessionState?, checkpointIdentity: HoldingIdentity):
        Pair<HoldingIdentity?, HoldingIdentity?> {
    return when {
        sessionState == null -> {
            Pair(null, null)
        }
        sessionState.sessionId.contains(INITIATED_SESSION_ID_SUFFIX) -> {
            Pair(sessionState.counterpartyIdentity, checkpointIdentity)
        }
        else -> {
            Pair(checkpointIdentity, sessionState.counterpartyIdentity)
        }
    }
}

fun Checkpoint.addOrReplaceSession(sessionState: SessionState) {
    val updated = sessions?.toMutableList()
    updated?.removeAll { it.sessionId == sessionState.sessionId }
    updated?.add(sessionState)
    sessions = updated
}