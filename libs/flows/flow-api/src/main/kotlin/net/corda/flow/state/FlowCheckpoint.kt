package net.corda.flow.state

import java.nio.ByteBuffer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant

/**
 * The FlowCheckpoint provides an API for managing the checkpoint during the processing of a flow.
 */
@Suppress("TooManyFunctions")
interface FlowCheckpoint : NonSerializable {
    val cpkFileHashes: Set<SecureHash>

    val flowId: String

    val flowKey: FlowKey

    val flowStartContext: FlowStartContext

    val holdingIdentity: HoldingIdentity

    var suspendedOn: String?

    var waitingFor: WaitingFor?

    val flowStack: FlowStack

    var serializedFiber: ByteBuffer

    val sessions: List<SessionState>

    var externalEventState: ExternalEventState?

    val doesExist: Boolean

    val currentRetryCount: Int

    val firstFailureTimestamp: Instant?

    val inRetryState: Boolean

    val retryEvent: FlowEvent

    val pendingPlatformError: ExceptionEnvelope?

    val flowContext: FlowContext

    val maxMessageSize: Long

    val initialPlatformVersion: Int

    val isCompleted: Boolean

    fun initFlowState(flowStartContext: FlowStartContext, cpkFileHashes: Set<SecureHash>)

    fun getSessionState(sessionId: String): SessionState?

    fun putSessionState(sessionState: SessionState)

    fun putSessionStates(sessionStates: List<SessionState>)

    fun markDeleted()

    fun rollback()

    fun markForRetry(flowEvent: FlowEvent, exception: Exception)

    fun markRetrySuccess()

    fun clearPendingPlatformError()

    fun setFlowSleepDuration(sleepTimeMs: Int)

    fun setPendingPlatformError(type: String, message: String)

    fun <T> readCustomState(clazz: Class<T>): T?

    fun writeCustomState(state: Any)

    fun toAvro(): Checkpoint?
}

