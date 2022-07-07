package net.corda.flow.state.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import java.nio.ByteBuffer

/**
 * Manages the state of the flow itself as the pipeline executes.
 *
 * Note that the flow state may need to be rolled back if some transient error occurs in the processing of a flow event
 * or a request out the fiber.
 */
class FlowStateManager(private val initialState: FlowState) {

    private var state = FlowState.newBuilder(initialState).build()
    var stack = FlowStackImpl(initialState.flowStackItems)

    private var sessionMap = validateAndCreateSessionMap(state.sessions)

    val flowKey : FlowKey = state.flowStartContext.statusKey

    val startContext : FlowStartContext = state.flowStartContext

    val holdingIdentity : HoldingIdentity = state.flowStartContext.identity

    val fiber : ByteBuffer
        get() = state.fiber ?: ByteBuffer.wrap(byteArrayOf())

    val sessions : List<SessionState>
        get() = sessionMap.values.toList()

    fun updateSuspendedFiber(fiber: ByteBuffer) {
        state.fiber = fiber
        state.suspendCount++
    }

    fun getSessionState(sessionId: String): SessionState? {
        return sessionMap[sessionId]
    }

    fun putSessionState(sessionState: SessionState) {
        sessionMap[sessionState.sessionId] = sessionState
    }

    var persistenceState : PersistenceState? = state.persistenceState

    var waitingFor : WaitingFor? = state.waitingFor

    var suspendedOn : String? = state.suspendedOn

    fun rollback() {
        state = FlowState.newBuilder(initialState).build()
        sessionMap = validateAndCreateSessionMap(state.sessions)
        stack = FlowStackImpl(initialState.flowStackItems)
    }

    fun toAvro(): FlowState {
        val sessions = sessionMap.values.sortedBy { it.sessionId }.toList()
        state.sessions = sessions
        state.flowStackItems = stack.flowStackItems
        return state
    }

    private fun validateAndCreateSessionMap(sessions: List<SessionState>) : MutableMap<String, SessionState> {
        val map = sessions.associateBy { it.sessionId }.toMutableMap()
        if (map.size != sessions.size) {
            // There is at least one duplicate, so identify duplicate ids and throw an error.
            val seen = mutableSetOf<String>()
            val duplicates = mutableSetOf<String>()
            sessions.forEach {
                if (it.sessionId in seen) {
                    duplicates.add(it.sessionId)
                } else {
                    seen.add(it.sessionId)
                }
            }
            throw IllegalStateException("Invalid checkpoint, flow ${state.flowStartContext.statusKey.id} has duplicate session for Session IDs = $duplicates")
        }
        return map
    }
}