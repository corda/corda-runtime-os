package net.corda.ledger.lib.impl.stub.checkpoint

import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.state.FlowContext
import net.corda.flow.state.FlowStack
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.util.*

@Suppress("UNUSED_PARAMETER")
class StubFlowCheckpoint : FlowCheckpoint {

    override val flowId: String
        get() = UUID.randomUUID().toString()
    override val suspendCount: Int
        get() = 0
    override val ledgerSaltCounter: Int
        get() = 0

    // We don't use the other properties in `PrivacySaltProviderServiceImpl`
    override val cpkFileHashes: Set<SecureHash>
        get() = TODO("Not yet implemented")
    override val flowKey: net.corda.data.flow.FlowKey
        get() = TODO("Not yet implemented")
    override val flowStartContext: net.corda.data.flow.FlowStartContext
        get() = TODO("Not yet implemented")
    override val holdingIdentity: net.corda.virtualnode.HoldingIdentity
        get() = TODO("Not yet implemented")
    override var suspendedOn: String?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var waitingFor: net.corda.data.flow.state.waiting.WaitingFor?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val flowStack: FlowStack
        get() = TODO("Not yet implemented")
    override var serializedFiber: ByteBuffer
        get() = TODO("Not yet implemented")
        set(value) {}
    override val sessions: List<net.corda.data.flow.state.session.SessionState>
        get() = TODO("Not yet implemented")
    override var externalEventState: net.corda.data.flow.state.external.ExternalEventState?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val doesExist: Boolean
        get() = TODO("Not yet implemented")
    override val pendingPlatformError: net.corda.data.ExceptionEnvelope?
        get() = TODO("Not yet implemented")
    override val flowContext: FlowContext
        get() = TODO("Not yet implemented")
    override val maxMessageSize: Long
        get() = TODO("Not yet implemented")
    override val maxPayloadSize: Long
        get() = TODO("Not yet implemented")
    override val initialPlatformVersion: Int
        get() = TODO("Not yet implemented")
    override val isCompleted: Boolean
        get() = TODO("Not yet implemented")
    override val outputs: List<net.corda.data.flow.state.checkpoint.SavedOutputs>
        get() = TODO("Not yet implemented")

    override fun initFlowState(flowStartContext: net.corda.data.flow.FlowStartContext, cpkFileHashes: Set<SecureHash>) {
        TODO("Not yet implemented")
    }

    override fun getSessionState(sessionId: String): net.corda.data.flow.state.session.SessionState? {
        TODO("Not yet implemented")
    }

    override fun putSessionState(sessionState: net.corda.data.flow.state.session.SessionState) {
        TODO("Not yet implemented")
    }

    override fun putSessionStates(sessionStates: List<net.corda.data.flow.state.session.SessionState>) {
        TODO("Not yet implemented")
    }

    override fun markDeleted() {
        TODO("Not yet implemented")
    }

    override fun rollback() {
        TODO("Not yet implemented")
    }

    override fun clearPendingPlatformError() {
        TODO("Not yet implemented")
    }

    override fun setPendingPlatformError(type: String, message: String) {
        TODO("Not yet implemented")
    }

    override fun <T> readCustomState(clazz: Class<T>): T? {
        TODO("Not yet implemented")
    }

    override fun writeCustomState(state: Any) {
        TODO("Not yet implemented")
    }

    override fun saveOutputs(savedOutputs: net.corda.data.flow.state.checkpoint.SavedOutputs) {
        TODO("Not yet implemented")
    }

    override fun toAvro(): net.corda.data.flow.state.checkpoint.Checkpoint? {
        TODO("Not yet implemented")
    }
}