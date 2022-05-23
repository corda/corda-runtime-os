package net.corda.flow.state.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.RetryState
import net.corda.data.flow.state.StateMachineState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_TRANSIENT_EXCEPTION
import net.corda.flow.state.FlowStack
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import java.lang.Exception
import java.nio.ByteBuffer
import java.time.Instant

class FlowCheckpointImpl(
    private var nullableCheckpoint: Checkpoint?,
    private val config: SmartConfig,
    private val instantProvider: () -> Instant
) : FlowCheckpoint {
    init {
        if (nullableCheckpoint != null) {

            checkNotNull(checkpoint.flowState) { "The flow state has not been set on the checkpoint." }
            checkNotNull(checkpoint.flowStartContext) { "The flow start context has not been set on the checkpoint." }

            // ensure all lists are initialised.
            checkpoint.sessions = checkpoint.sessions ?: mutableListOf()
            checkpoint.flowStackItems = checkpoint.flowStackItems ?: mutableListOf()
            nullableCheckpoint = checkpoint

            validateAndAddAll()
        }
    }

    private var nullableSuspendOn: String? = null
    private var nullableWaitingFor: WaitingFor? = null
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
        get() = nullableSuspendOn
        set(value) {
            nullableSuspendOn = value
        }

    override var waitingFor: WaitingFor?
        get() = nullableWaitingFor
        set(value) {
            nullableWaitingFor = value
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

    override val doesExist: Boolean
        get() = nullableCheckpoint != null

    override val currentRetryCount: Int
        get() = checkpoint.retryState?.retryCount ?: -1

    override val inRetryState: Boolean
        get() = doesExist && checkpoint.retryState != null

    override val retryEvent: FlowEvent
        get() = checkNotNull(checkpoint.retryState)
        { "Attempt to access null retry state while. inRetryState must be tested before accessing retry fields" }
            .failedEvent

    override fun initFromNew(flowId: String, flowStartContext: FlowStartContext) {
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
            .setWaitingFor(null)
            .setSuspendedOn(null)
            .build()

        nullableCheckpoint = Checkpoint.newBuilder()
            .setFlowId(flowId)
            .setFiber(ByteBuffer.wrap(byteArrayOf()))
            .setFlowStartContext(flowStartContext)
            .setFlowState(state)
            .setSessions(mutableListOf())
            .setFlowStackItems(mutableListOf())
            .setMaxFlowSleepDuration(config.getInt(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION))
            .build()

        validateAndAddAll()
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

    override fun rollback() {
        validateAndAddAll()
    }

    override fun markForRetry(flowEvent: FlowEvent, exception: Exception) {
        if (checkpoint.retryState == null) {
            checkpoint.retryState = RetryState().apply {
                retryCount = 1
                failedEvent = flowEvent
                error = createAvroExceptionEnvelope(exception)
                firstFailureTimestamp = instantProvider()
                lastFailureTimestamp = firstFailureTimestamp
            }
        } else {
            checkpoint.retryState.retryCount++
            checkpoint.retryState.error = createAvroExceptionEnvelope(exception)
            checkpoint.retryState.lastFailureTimestamp = instantProvider()
        }
    }

    override fun markRetrySuccess() {
        checkpoint.retryState = null
    }

    override fun toAvro(): Checkpoint? {
        if (nullableCheckpoint == null) {
            return null
        }

        checkpoint.flowState.suspendedOn = nullableSuspendOn
        checkpoint.flowState.waitingFor = nullableWaitingFor
        checkpoint.sessions = sessionMap.values.toList()
        checkpoint.flowStackItems = nullableFlowStack?.flowStackItems ?: emptyList()
        return checkpoint
    }

    private fun validateAndAddAll() {
        validateAndAddStateFields()
        validateAndAddSessions()
        validateAndAddFlowStack()
    }

    private fun validateAndAddStateFields() {
        nullableSuspendOn = checkpoint.flowState.suspendedOn
        nullableWaitingFor = checkpoint.flowState.waitingFor
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

        nullableFlowStack = FlowStackImpl(checkpoint.flowStackItems.toMutableList())
    }

    private fun createAvroExceptionEnvelope(exception: Exception): ExceptionEnvelope {
        return ExceptionEnvelope().apply {
            errorType = FLOW_TRANSIENT_EXCEPTION
            errorMessage = exception.message
        }
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