package net.corda.flow.state.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.persistence.PersistenceState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowStack
import net.corda.libs.configuration.SmartConfig
import java.nio.ByteBuffer
import java.time.Instant

@Suppress("TooManyFunctions")
class FlowCheckpointImpl(
    private val checkpoint: Checkpoint,
    config: SmartConfig,
    instantProvider: () -> Instant
) : FlowCheckpoint {

    private val pipelineStateManager = PipelineStateManager(checkpoint.pipelineState, config, instantProvider)
    private var flowStateManager = checkpoint.flowState?.let {
        FlowStateManager(it)
    }
    private var nullableFlowStack: FlowStackImpl? = checkpoint.flowState?.let {
        FlowStackImpl(it.flowStackItems)
    }

    private var deleted = false

    private val flowInitialisedOnCreation = checkpoint.flowState != null

    // The checkpoint is live if it is not marked deleted and there is either some flow state, or a retry is currently
    // occurring (for example, if a transient failure has happened while processing a start event).
    private val checkpointLive : Boolean
        get() = !deleted && (flowStateManager != null || inRetryState)

    override val flowId: String
        get() = checkpoint.flowId

    override val flowKey: FlowKey
        get() = flowStateManager?.flowKey
            ?: throw IllegalStateException("Attempted to access flow key before the flow has been initialised.")

    override val flowStartContext: FlowStartContext
        get() = flowStateManager?.startContext
            ?: throw IllegalStateException("Attempted to access flow start context before the flow has been initialised.")

    override val holdingIdentity: HoldingIdentity
        get() = flowStateManager?.holdingIdentity
            ?: throw IllegalStateException("Attempted to access holding identity before the flow has been initialised.")

    override var suspendedOn: String?
        get() = flowStateManager?.suspendedOn
        set(value) {
            checkNotNull(flowStateManager) {
                "Attempted to access checkpoint to set suspendedOn before the flow has been initialised."
            }.suspendedOn = value
        }

    override var waitingFor: WaitingFor?
        get() = flowStateManager?.waitingFor
        set(value) {
            checkNotNull(flowStateManager) {
                "Attempted to access checkpoint to set waitingFor before the flow has been initialised."
            }.waitingFor = value
        }

    override val flowStack: FlowStack
        get() = checkNotNull(flowStateManager)
        { "Attempt to access checkpoint before initialisation" }.stack

    override var serializedFiber: ByteBuffer
        get() = flowStateManager?.fiber ?: throw IllegalStateException("Attempted to access flow fiber before the flow has been initialised.")
        set(value) {
            checkNotNull(flowStateManager) {
                "Attempt to set the flow state before it has been created"
            }.updateSuspendedFiber(value)
        }

    override val sessions: List<SessionState>
        get() = flowStateManager?.sessions ?: throw IllegalStateException("Attempted to access sessions before the flow has been initialised.")

    override var persistenceState: PersistenceState?
        get() = flowStateManager?.persistenceState
        set(value) {
            checkNotNull(flowStateManager) {
                "Attempt to set the flow state before it has been created"
            }.persistenceState = value
        }

    override val doesExist: Boolean
        get() = flowStateManager != null && !deleted

    override val currentRetryCount: Int
        get() = pipelineStateManager.retryCount

    override val inRetryState: Boolean
        get() = pipelineStateManager.retryState != null

    override val retryEvent: FlowEvent
        get() = pipelineStateManager.retryEvent

    override val pendingPlatformError: ExceptionEnvelope?
        get() = checkpoint.pipelineState.pendingPlatformError

    override fun initFlowState(flowStartContext: FlowStartContext) {
        if (flowStateManager != null) {
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

        flowStateManager = FlowStateManager(flowState)
        nullableFlowStack = FlowStackImpl(flowState.flowStackItems)
    }

    override fun getSessionState(sessionId: String): SessionState? {
        val manager = flowStateManager ?: throw IllegalStateException("Attempted to get a session state before the flow has been initialised.")
        return manager.getSessionState(sessionId)
    }

    override fun putSessionState(sessionState: SessionState) {
        checkFlowNotDeleted()
        val manager = flowStateManager ?: throw IllegalStateException("Attempted to set a session state before the flow has been initialised.")
        manager.putSessionState(sessionState)
    }

    override fun markDeleted() {
        deleted = true
    }

    override fun rollback() {
        if (flowInitialisedOnCreation) {
            flowStateManager?.rollback()
        } else {
            // The flow was initialised as part of processing this event, so on rollback the flow state should be
            // removed. Next time the event is processed, the flow data will be recreated.
            flowStateManager = null
        }
    }

    override fun markForRetry(flowEvent: FlowEvent, exception: Exception) {
        checkFlowNotDeleted()
        pipelineStateManager.retry(flowEvent, exception)
    }

    override fun markRetrySuccess() {
        checkFlowNotDeleted()
        pipelineStateManager.markRetrySuccess()
    }

    override fun clearPendingPlatformError() {
        pipelineStateManager.clearPendingPlatformError()
    }

    override fun setFlowSleepDuration(sleepTimeMs: Int) {
        checkFlowNotDeleted()
        pipelineStateManager.setFlowSleepDuration(sleepTimeMs)
    }

    override fun setPendingPlatformError(type: String, message: String) {
        pipelineStateManager.setPendingPlatformError(type, message)
    }

    override fun toAvro(): Checkpoint? {
        if (!checkpointLive) {
            return null
        }

        checkpoint.pipelineState = pipelineStateManager.toAvro()
        checkpoint.flowState = flowStateManager?.toAvro()
        return checkpoint
    }

    private fun checkFlowNotDeleted() {
        // Does not prevent changes to the Avro objects, but will give us some protection from bugs moving forward.
        check(!deleted) { "Flow has been marked for deletion but is currently being modified" }
    }
}