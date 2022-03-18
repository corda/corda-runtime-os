package net.corda.flow.state.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.state.FlowStack
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import java.nio.ByteBuffer

class FlowCheckpointImpl(private var nullableCheckpoint: Checkpoint?) : FlowCheckpoint {
    init {
        if (nullableCheckpoint != null) {

            checkNotNull(checkpoint.flowState){"The flow state has not been set on the checkpoint."}
            checkNotNull(checkpoint.flowStartContext){"The flow start context has not been set on the checkpoint."}

            // ensure all lists are initialised.
            checkpoint.sessions = checkpoint.sessions ?: mutableListOf()
            checkpoint.flowStackItems = checkpoint.flowStackItems ?: mutableListOf()
            nullableCheckpoint = checkpoint

            validateAndAddSessions()
            validateAndAddFlowStack()
        }
    }

    private var nullableSessionsMap: MutableMap<String, SessionState>? = null
    private var nullableFlowStack: FlowStackImpl? = null

    private val checkpoint: Checkpoint
        get() = checkNotNull(nullableCheckpoint)
        { "Attempt to access checkpoint before initialisation" }

    private val sessionMap: MutableMap<String, SessionState>
        get() = checkNotNull(nullableSessionsMap)
        { "Attempt to access checkpoint before initialisation" }

    override val flowId: String
        get() = checkpoint.flowId

    override val flowKey: FlowKey
        get() = checkpoint.flowStartContext.statusKey

    override val flowStartContext: FlowStartContext
        get() = checkpoint.flowStartContext

    override val holdingIdentity: HoldingIdentity
        get() = checkpoint.flowStartContext.identity

    override var suspendedOn: String?
        get() = checkpoint.flowState.suspendedOn
        set(value) {
            checkpoint.flowState.suspendedOn = value
        }

    override var waitingFor: WaitingFor
        get() = checkpoint.flowState.waitingFor
        set(value) {
            checkpoint.flowState.waitingFor = value
        }

    override val flowStack: FlowStack
        get() = checkNotNull(nullableFlowStack)
        { "Attempt to access checkpoint before initialisation" }

    override var serializedFiber: ByteBuffer
        get() = checkpoint.fiber ?: ByteBuffer.wrap(byteArrayOf())
        set(value) {
            checkpoint.fiber = value
        }

    override val sessions: List<SessionState>
        get() = sessionMap.values.toList()

    override fun initFromNew(flowId: String, flowStartContext: FlowStartContext, waitingFor: WaitingFor) {
        if (nullableCheckpoint != null) {
            val key = flowStartContext.statusKey
            throw IllegalStateException(
                "Found existing checkpoint while starting to start a new flow." +
                        " Flow ID='${flowId}' FlowKey='${key.id}-${key.identity.x500Name}."
            )
        }

        val state = StateMachineState.newBuilder()
            .setSuspendCount(0)
            .setIsKilled(false)
            .setWaitingFor(waitingFor)
            .setSuspendedOn(null)
            .build()

        nullableCheckpoint = Checkpoint.newBuilder()
            .setFlowId(flowId)
            .setFiber(ByteBuffer.wrap(byteArrayOf()))
            .setFlowStartContext(flowStartContext)
            .setFlowState(state)
            .setSessions(mutableListOf())
            .setFlowStackItems(mutableListOf())
            .build()

        validateAndAddSessions()
        validateAndAddFlowStack()
    }

    override fun getSessionState(sessionId: String): SessionState? {
        return sessionMap[sessionId]
    }

    override fun putSessionState(sessionState: SessionState) {
        sessionMap[sessionState.sessionId] = sessionState
    }

    override fun markDeleted() {
        nullableCheckpoint = null
    }

    override fun toAvro(): Checkpoint? {
        if (nullableCheckpoint == null) {
            return null
        }

        checkpoint.sessions = sessionMap.values.toList()
        checkpoint.flowStackItems = nullableFlowStack?.flowStackItems?.toList() ?: emptyList()
        return checkpoint
    }

    private fun validateAndAddSessions() {
        nullableSessionsMap = mutableMapOf()

        checkpoint.sessions.forEach {
            if (sessionMap.containsKey(it.sessionId)) {
                val message =
                    "Invalid checkpoint, flow '${flowId}' has duplicate session for Session ID = '${it.sessionId}'"
                throw IllegalStateException(message)
            }
            sessionMap[it.sessionId] = it
        }
    }

    private fun validateAndAddFlowStack() {
        checkpoint.flowStackItems.forEach {
            it.sessionIds = it.sessionIds ?: mutableListOf()
        }

        nullableFlowStack = FlowStackImpl(checkpoint.flowStackItems)
    }

    private class FlowStackImpl(val flowStackItems: MutableList<FlowStackItem>) : FlowStack {

        override val size: Int get() = flowStackItems.size

        override fun push(flow: Flow<*>): FlowStackItem {
            val stackItem = FlowStackItem(flow.javaClass.name, flow.getIsInitiatingFlow(), mutableListOf())
            flowStackItems.add(stackItem)
            return stackItem
        }

        override fun nearestFirst(predicate: (FlowStackItem) -> Boolean): FlowStackItem? {
            return flowStackItems.reversed().firstOrNull(predicate)
        }

        override fun peek(): FlowStackItem? {
            return flowStackItems.lastOrNull()
        }

        override fun peekFirst(): FlowStackItem {
            val firstItem = flowStackItems.firstOrNull()
            return checkNotNull(firstItem) { "peekFirst() was called on an empty stack." }
        }

        override fun pop(): FlowStackItem? {
            if (flowStackItems.size == 0) {
                return null
            }
            val stackEntry = flowStackItems.last()
            flowStackItems.removeLast()
            return stackEntry
        }

        private fun Flow<*>.getIsInitiatingFlow(): Boolean {
            var current: Class<in Flow<*>> = this.javaClass

            while (true) {
                val annotation = current.getDeclaredAnnotation(InitiatingFlow::class.java)
                if (annotation != null) {
                    require(annotation.version > 0) { "Flow versions have to be greater or equal to 1" }
                    return true
                }

                current = current.superclass
                    ?: return false
            }
        }
    }
}