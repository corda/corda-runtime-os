package net.corda.flow.state.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.checkpoint.RetryState
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_TRANSIENT_EXCEPTION
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

@Suppress("TooManyFunctions")
class FlowCheckpointImpl(
    private val checkpoint: Checkpoint,
    private val config: SmartConfig,
    private val instantProvider: () -> Instant
) : FlowCheckpoint {

    companion object {
        const val RETRY_INITIAL_DELAY_MS = 1000 // 1 second
    }

    private var nullableSuspendOn: String? = null
    private var nullableWaitingFor: WaitingFor? = null
    private var nullableSessionsMap: MutableMap<String, SessionState>? = null
    private var nullableFlowStack: FlowStackImpl? = null
    private var nullablePersistenceState: PersistenceState? = null

    private val sessionMap: MutableMap<String, SessionState>
        get() = checkNotNull(nullableSessionsMap)
        { "Attempt to access checkpoint before initialisation" }

    private var deleted = false

    override val flowId: String
        get() = checkpoint.flowId

    override val flowKey: FlowKey
        get() = checkpoint.flowState?.flowStartContext?.statusKey
            ?: throw IllegalStateException("Attempted to access flow key before the flow has been initialised.")

    override val flowStartContext: FlowStartContext
        get() = checkpoint.flowState?.flowStartContext
            ?: throw IllegalStateException("Attempted to access flow start context before the flow has been initialised.")

    override val holdingIdentity: HoldingIdentity
        get() = checkpoint.flowState?.flowStartContext?.identity
            ?: throw IllegalStateException("Attempted to access holding identity before the flow has been initialised.")

    override var suspendedOn: String? = null

    override var waitingFor: WaitingFor? = null

    override val flowStack: FlowStack
        get() = checkNotNull(nullableFlowStack)
        { "Attempt to access checkpoint before initialisation" }

    override var serializedFiber: ByteBuffer
        get() = checkpoint.flowState?.fiber ?: ByteBuffer.wrap(byteArrayOf())
        set(value) {
            checkNotNull(checkpoint.flowState) {
                "Attempt to set the flow state before it has been created"
            }.fiber = value
        }

    override val sessions: List<SessionState>
        get() = sessionMap.values.toList()

    override var persistenceState: PersistenceState? = checkpoint.flowState?.persistenceState

    override val doesExist: Boolean
        get() = checkpoint.flowState != null && !deleted

    override val currentRetryCount: Int
        get() = checkpoint.pipelineState.retryState?.retryCount ?: -1

    override val inRetryState: Boolean
        get() = doesExist && checkpoint.pipelineState.retryState != null

    override val retryEvent: FlowEvent
        get() = checkNotNull(checkpoint.pipelineState.retryState)
        { "Attempt to access null retry state while. inRetryState must be tested before accessing retry fields" }
            .failedEvent

    override val pendingPlatformError: ExceptionEnvelope?
        get() = checkpoint.pipelineState.pendingPlatformError

    override fun initFlowState(flowStartContext: FlowStartContext) {
        if (checkpoint.flowState != null) {
            val key = flowStartContext.statusKey
            throw IllegalStateException(
                "Found existing checkpoint while starting to start a new flow." +
                        " Flow ID='${flowId}' FlowKey='${key.id}-${key.identity.x500Name}."
            )
        }

        val flowState = FlowState.newBuilder().apply {
            fiber = ByteBuffer.wrap(byteArrayOf())
            setFlowStartContext(flowStartContext)
            persistenceState = null
            sessions = mutableListOf()
            flowStackItems = mutableListOf()
            waitingFor = null
            suspendCount = 0
            suspendedOn = null
        }.build()

        checkpoint.flowState = flowState

        validateAndAddAll()
    }

    override fun getSessionState(sessionId: String): SessionState? {
        return sessionMap[sessionId]
    }

    override fun putSessionState(sessionState: SessionState) {
        checkFlowNotDeleted()
        sessionMap[sessionState.sessionId] = sessionState
    }

    override fun markDeleted() {
        deleted = true
    }

    override fun rollback() {
        validateAndAddAll()
    }

    override fun markForRetry(flowEvent: FlowEvent, exception: Exception) {
        checkFlowNotDeleted()
        if (checkpoint.pipelineState.retryState == null) {
            checkpoint.pipelineState.retryState = RetryState().apply {
                retryCount = 1
                failedEvent = flowEvent
                error = createAvroExceptionEnvelope(exception)
                firstFailureTimestamp = instantProvider()
                lastFailureTimestamp = firstFailureTimestamp
            }
        } else {
            checkpoint.pipelineState.retryState.retryCount++
            checkpoint.pipelineState.retryState.error = createAvroExceptionEnvelope(exception)
            checkpoint.pipelineState.retryState.lastFailureTimestamp = instantProvider()
        }

        val maxRetrySleepTime = config.getInt(FlowConfig.PROCESSING_MAX_RETRY_DELAY)
        val sleepTime = (2.0.pow(checkpoint.pipelineState.retryState.retryCount - 1.toDouble())) * RETRY_INITIAL_DELAY_MS
        setFlowSleepDuration(min(maxRetrySleepTime, sleepTime.toInt()))
    }

    override fun markRetrySuccess() {
        checkFlowNotDeleted()
        checkpoint.pipelineState.retryState = null
    }

    override fun clearPendingPlatformError() {
        checkpoint.pipelineState.pendingPlatformError = null
    }

    override fun setFlowSleepDuration(sleepTimeMs: Int) {
        checkFlowNotDeleted()
        checkpoint.pipelineState.maxFlowSleepDuration = min(sleepTimeMs, checkpoint.pipelineState.maxFlowSleepDuration)
    }

    override fun setPendingPlatformError(type: String, message: String) {
        checkpoint.pipelineState.pendingPlatformError = ExceptionEnvelope().apply {
            errorType = type
            errorMessage = message
        }
    }

    override fun toAvro(): Checkpoint? {
        if (deleted) {
            return null
        }

        checkpoint.flowState.persistenceState = nullablePersistenceState
        checkpoint.flowState.suspendedOn = nullableSuspendOn
        checkpoint.flowState.waitingFor = nullableWaitingFor
        checkpoint.flowState.sessions = sessionMap.values.toList()
        checkpoint.flowState.flowStackItems = nullableFlowStack?.flowStackItems ?: emptyList()
        return checkpoint
    }

    private fun validateAndAddAll() {
        validateAndAddStateFields()
        validateAndAddSessions()
        validateAndAddFlowStack()
        validateAndAddPersistenceState()
    }

    private fun validateAndAddPersistenceState() {
        nullablePersistenceState = checkpoint.flowState.persistenceState
    }

    private fun validateAndAddStateFields() {
        nullableSuspendOn = checkpoint.flowState.suspendedOn
        nullableWaitingFor = checkpoint.flowState.waitingFor
    }

    private fun validateAndAddSessions() {
        nullableSessionsMap = mutableMapOf()

        checkpoint.flowState.sessions.forEach {
            if (sessionMap.containsKey(it.sessionId)) {
                val message =
                    "Invalid checkpoint, flow '${flowId}' has duplicate session for Session ID = '${it.sessionId}'"
                throw IllegalStateException(message)
            }
            sessionMap[it.sessionId] = it
        }
    }

    private fun validateAndAddFlowStack() {
        checkpoint.flowState.flowStackItems.forEach {
            it.sessionIds = it.sessionIds ?: mutableListOf()
        }

        nullableFlowStack = FlowStackImpl(checkpoint.flowState.flowStackItems.toMutableList())
    }

    private fun createAvroExceptionEnvelope(exception: Exception): ExceptionEnvelope {
        return ExceptionEnvelope().apply {
            errorType = FLOW_TRANSIENT_EXCEPTION
            errorMessage = exception.message
        }
    }

    private fun checkFlowNotDeleted() {
        // Does not prevent changes to the Avro objects, but will give us some protection from bugs moving forward.
        check(!deleted) { "Flow has been marked for deletion but is currently being modified" }
    }

    private class FlowStackImpl(val flowStackItems: MutableList<FlowStackItem>) : FlowStack {

        override val size: Int get() = flowStackItems.size

        override fun push(flow: Flow): FlowStackItem {
            val stackItem =
                FlowStackItem(flow::class.java.name, flow::class.java.getIsInitiatingFlow(), mutableListOf())
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

        private fun Class<*>.getIsInitiatingFlow(): Boolean {
            return this.getDeclaredAnnotation(InitiatingFlow::class.java) != null
        }
    }
}