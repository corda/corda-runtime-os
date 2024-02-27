package net.corda.flow.state.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.state.checkpoint.PipelineState
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer

/**
 * Manages the state of the pipeline while the flow is executing.
 *
 * Pipeline state should be set up immediately on processing a flow event (even if the flow has just been started). It
 * is not rolled back in the event of transient failures, as it records problems that occur during processing.
 */
class PipelineStateManager(
    private val state: PipelineState,
) {

    val cpkFileHashes: Set<SecureHash>
        get() = state.cpkFileHashes.map { SecureHashImpl(it.algorithm, it.bytes.array()) }.toSet()

    fun populateCpkFileHashes(cpkFileHashes: Set<SecureHash>) {
        if (state.cpkFileHashes.isNullOrEmpty()) {
            state.cpkFileHashes = cpkFileHashes.map { net.corda.data.crypto.SecureHash(it.algorithm, ByteBuffer.wrap(it.bytes)) }
        } else {
            throw IllegalStateException("cpk file hash list ${state.cpkFileHashes} cannot be updated to $cpkFileHashes once set")
        }
    }

    fun clearCpkFileHashes() {
        state.cpkFileHashes = emptyList()
    }

    fun setPendingPlatformError(type: String, message: String) {
        state.pendingPlatformError = ExceptionEnvelope().apply {
            errorType = type
            errorMessage = message
        }
    }

    fun clearPendingPlatformError() {
        state.pendingPlatformError = null
    }

    fun toAvro(): PipelineState {
        return state
    }
}
