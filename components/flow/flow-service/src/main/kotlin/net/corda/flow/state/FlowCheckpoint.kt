package net.corda.flow.state

import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.identity.HoldingIdentity
import net.corda.serialization.checkpoint.NonSerializable
import java.lang.Exception
import java.nio.ByteBuffer

/**
 * The FlowCheckpoint provides an API for managing the checkpoint during the processing of a flow.
 */
interface FlowCheckpoint : NonSerializable {

    val flowId: String

    val flowKey: FlowKey

    val flowStartContext: FlowStartContext

    val holdingIdentity: HoldingIdentity

    var suspendedOn: String?

    var waitingFor: WaitingFor

    val flowStack: FlowStack

    var serializedFiber: ByteBuffer

    val sessions: List<SessionState>

    val doesExist: Boolean

    val currentRetryCount: Int

    val inRetryState: Boolean

    val retryEvent: FlowEvent

    fun initFromNew(flowId: String, flowStartContext: FlowStartContext, waitingFor: WaitingFor)

    fun getSessionState(sessionId: String): SessionState?

    fun putSessionState(sessionState: SessionState)

    fun markDeleted()

    fun rollback()

    fun markForRetry(flowEvent: FlowEvent, exception: Exception)

    fun markRetrySuccess()

    fun toAvro(): Checkpoint?
}

