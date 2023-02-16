package net.corda.flow.state

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
import java.nio.ByteBuffer

/**
 * The FlowCheckpoint provides an API for managing the checkpoint during the processing of a flow.
 */
interface FlowCheckpoint : NonSerializable {
    val cpks: Set<SecureHash>

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

    val inRetryState: Boolean

    val retryEvent: FlowEvent

    val pendingPlatformError: ExceptionEnvelope?

    val flowContext: FlowContext

    val maxMessageSize: Long

    fun initFlowState(flowStartContext: FlowStartContext, cpks: Set<SecureHash>)

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

    fun toAvro(): Checkpoint?
}

