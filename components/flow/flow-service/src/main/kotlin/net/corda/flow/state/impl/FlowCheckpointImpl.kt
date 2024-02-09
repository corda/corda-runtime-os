package net.corda.flow.state.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.corda.data.ExceptionEnvelope
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.FlowState
import net.corda.data.flow.state.checkpoint.SavedOutputs
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowContext
import net.corda.flow.state.FlowStack
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

@Suppress("TooManyFunctions")
class FlowCheckpointImpl(
    private val checkpoint: Checkpoint,
    private val config: SmartConfig,
) : FlowCheckpoint {

    /**
     * It's important that we guard against null values for default fields added between versions of the Avro object.
     * An edge condition exists where an existing checkpoint for a previous version can be loaded
     * by the newer version. In these cases any new, default fields will be null. initFlowState() is only called when
     * the checkpoint is first created, so it's important we check these fields each time we create an instance
     * of FlowCheckpointImpl.
     */
    init {
        if (checkpoint.customState == null) {
            val newCustomState = KeyValuePairList.newBuilder()
                .setItems(listOf())
                .build()
            checkpoint.customState = newCustomState
        }
    }

    private companion object {
        val objectMapper = ObjectMapper().registerKotlinModule()
    }

    private val pipelineStateManager = PipelineStateManager(checkpoint.pipelineState)
    private var flowStateManager = checkpoint.flowState?.let(::FlowStateManager)
    private var nullableFlowStack: FlowStackImpl? = checkpoint.flowState?.let {
        FlowStackImpl(it.flowStackItems)
    }

    private var deleted = false
    private val ledgerSaltIncrementor = AtomicInteger(0)

    private val flowInitialisedOnCreation = checkpoint.flowState != null

    // The checkpoint is live if it is not marked deleted and there is some flow state.
    private val checkpointLive: Boolean
        get() = !deleted && (flowStateManager != null)

    override val flowId: String
        get() = checkpoint.flowId

    override val flowKey: FlowKey
        get() = checkNotNull(flowStateManager) {
            "Attempted to access flow key before the flow has been initialised."
        }.flowKey

    override val flowStartContext: FlowStartContext
        get() = checkNotNull(flowStateManager) {
            "Attempted to access flow start context before the flow has been initialised."
        }.startContext

    override val holdingIdentity: HoldingIdentity
        get() = checkNotNull(flowStateManager) {
            "Attempted to access holding identity before the flow has been initialised."
        }.holdingIdentity

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
        get() = flowStateManager?.fiber
            ?: throw IllegalStateException("Attempted to access flow fiber before the flow has been initialised.")
        set(value) {
            checkNotNull(flowStateManager) {
                "Attempt to set the flow state before it has been created"
            }.updateSuspendedFiber(value)
        }

    override val sessions: List<SessionState>
        get() = flowStateManager?.sessions
            ?: throw IllegalStateException("Attempted to access sessions before the flow has been initialised.")

    override var externalEventState: ExternalEventState?
        get() = flowStateManager?.externalEventState
        set(value) {
            checkNotNull(flowStateManager) {
                "Attempt to set the flow state before it has been created"
            }.externalEventState = value
        }

    override val doesExist: Boolean
        get() = flowStateManager != null && !deleted

    override val cpkFileHashes: Set<SecureHash>
        get() = pipelineStateManager.cpkFileHashes

    override val pendingPlatformError: ExceptionEnvelope?
        get() = checkpoint.pipelineState.pendingPlatformError

    override val flowContext: FlowContext
        get() = checkNotNull(flowStateManager)
        { "Attempt to access context before flow state has been created" }.flowContext

    override val maxMessageSize: Long
        get() = config.getLong(MAX_ALLOWED_MSG_SIZE)

    override val initialPlatformVersion: Int
        get() = checkpoint.initialPlatformVersion

    override val isCompleted: Boolean
        get() = deleted

    override val suspendCount: Int
        get() = checkNotNull(flowStateManager)
        { "Attempt to access context before flow state has been created" }.suspendCount

    override val outputs: List<SavedOutputs>
        get() = checkpoint.savedOutputs ?: emptyList()

    override val ledgerSaltCounter: Int
        get() = ledgerSaltIncrementor.getAndIncrement()

    override fun initFlowState(flowStartContext: FlowStartContext, cpkFileHashes: Set<SecureHash>) {
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
            externalEventState = null
            sessions = mutableListOf()
            flowStackItems = mutableListOf()
            waitingFor = null
            suspendCount = 0
            suspendedOn = null
        }.build()

        flowStateManager = FlowStateManager(flowState)
        nullableFlowStack = FlowStackImpl(flowState.flowStackItems)
        pipelineStateManager.populateCpkFileHashes(cpkFileHashes)
    }

    override fun getSessionState(sessionId: String): SessionState? {
        val manager = flowStateManager
            ?: throw IllegalStateException("Attempted to get a session state before the flow has been initialised.")
        return manager.getSessionState(sessionId)
    }

    override fun putSessionState(sessionState: SessionState) {
        checkFlowNotDeleted()
        val manager = flowStateManager
            ?: throw IllegalStateException("Attempted to set a session state before the flow has been initialised.")
        manager.putSessionState(sessionState)
    }

    override fun putSessionStates(sessionStates: List<SessionState>) {
        checkFlowNotDeleted()
        val manager = flowStateManager
            ?: throw IllegalStateException("Attempted to set session states before the flow has been initialised.")
        sessionStates.forEach {
            manager.putSessionState(it)
        }
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
            pipelineStateManager.clearCpkFileHashes()
        }
    }

    override fun clearPendingPlatformError() {
        pipelineStateManager.clearPendingPlatformError()
    }

    override fun setPendingPlatformError(type: String, message: String) {
        pipelineStateManager.setPendingPlatformError(type, message)
    }

    override fun writeCustomState(state: Any) {
        val key = state.javaClass.name
        val newState = KeyValuePair.newBuilder()
            .setKey(key)
            .setValue(objectMapper.writeValueAsString(state))
            .build()

        checkpoint.customState = KeyValuePairList
            .newBuilder()
            .setItems(checkpoint.customState.items.filterNot { it.key == key } + newState)
            .build()
    }

    override fun saveOutputs(savedOutputs: SavedOutputs) {
        val existingOutputs = checkpoint.savedOutputs ?: emptyList()
        checkpoint.savedOutputs = existingOutputs.plus(savedOutputs)
    }

    override fun <T> readCustomState(clazz: Class<T>): T? {
        return checkpoint.customState.items
            .firstOrNull { it.key == clazz.name }
            ?.let {
                objectMapper.readValue(it.value, clazz)
            }
    }

    override fun toAvro(): Checkpoint? {
        if (flowStateManager == null) {
            //set to null when rollback to initial null state or not initialized
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
