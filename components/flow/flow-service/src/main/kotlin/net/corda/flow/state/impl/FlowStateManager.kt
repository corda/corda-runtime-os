package net.corda.flow.state.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

/**
 * Manages the state of the flow itself as the pipeline executes.
 *
 * Note that the flow state may need to be rolled back if some transient error occurs in the processing of a flow event
 * or a request out the fiber.
 */
class FlowStateManager(private val initialState: FlowState) {

    private var state = FlowState.newBuilder(initialState).build()

    private var sessionMap = validateAndCreateSessionMap(state.sessions)

    var stack = FlowStackImpl(state.flowStackItems)

    val flowKey: FlowKey = state.flowStartContext.statusKey

    val startContext: FlowStartContext = state.flowStartContext

    val holdingIdentity: HoldingIdentity = state.flowStartContext.identity.toCorda()

    val fiber: ByteBuffer
        get() = state.fiber ?: ByteBuffer.wrap(byteArrayOf())

    val sessions: List<SessionState>
        get() = sessionMap.values.toList()

    var flowContext = FlowStackBasedContext(stack)

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

    var externalEventState: ExternalEventState?
        get() = state.externalEventState
        set(value) {
            state.externalEventState = value
        }

    var waitingFor: WaitingFor?
        get() = state.waitingFor
        set(value) {
            state.waitingFor = value
        }

    var suspendedOn: String?
        get() = state.suspendedOn
        set(value) {
            state.suspendedOn = value
        }

    fun rollback() {
        var retainedEventState: ExternalEventState? = null
        this.externalEventState?.let {
            retainedEventState = ExternalEventState.newBuilder(state.externalEventState).build()
        }

        state = FlowState.newBuilder(initialState).build().apply {
            externalEventState = retainedEventState
        }

        sessionMap = validateAndCreateSessionMap(state.sessions)
        stack = FlowStackImpl(state.flowStackItems)
        flowContext = FlowStackBasedContext(stack)
    }

    fun toAvro(): FlowState {
        val sessions = sessionMap.values.sortedBy { it.sessionId }.toList()
        state.sessions = sessions
        state.flowStackItems = stack.flowStackItems
        return state
    }

    private fun validateAndCreateSessionMap(sessions: List<SessionState>): MutableMap<String, SessionState> {
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
            throw IllegalStateException(
                "Invalid checkpoint, flow ${state.flowStartContext.statusKey.id} has duplicate session " +
                        "for Session IDs = $duplicates"
            )
        }
        return map
    }
}