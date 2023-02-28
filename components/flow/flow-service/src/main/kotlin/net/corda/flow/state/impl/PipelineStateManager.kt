package net.corda.flow.state.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.PipelineState
import net.corda.data.flow.state.checkpoint.RetryState
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig
import net.corda.v5.crypto.SecureHash
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages the state of the pipeline while the flow is executing.
 *
 * Pipeline state should be set up immediately on processing a flow event (even if the flow has just been started). It
 * is not rolled back in the event of transient failures, as it records problems that occur during processing.
 */
class PipelineStateManager(
    private val state: PipelineState,
    private val config: SmartConfig,
    private val instantProvider: () -> Instant
) {

    private companion object {
        private const val RETRY_INITIAL_DELAY_MS = 1000
    }

    init {
        // Reset the max sleep time
        state.maxFlowSleepDuration = config.getInt(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION)
    }

    val cpkFileHashes: Set<SecureHash>
        get() = state.cpkFileHashes.map { SecureHash(it.algorithm, it.bytes.array()) }.toSet()

    val retryState: RetryState?
        get() = state.retryState

    val retryEvent: FlowEvent
        get() = state.retryState?.failedEvent
            ?: throw IllegalStateException("Attempt to access null retry state. inRetryState must be tested before accessing retry fields")

    val retryCount: Int
        get() = state.retryState?.retryCount ?: -1

    /**
     * Update the current pipeline state to set a retry of the current event.
     */
    fun retry(event: FlowEvent, exception: Exception) {
        val timestamp = instantProvider()
        val retryState = state.retryState ?: RetryState().apply {
            retryCount = 0
            failedEvent = event
            firstFailureTimestamp = timestamp
        }
        retryState.retryCount++
        retryState.error = createAvroExceptionEnvelope(exception)
        retryState.lastFailureTimestamp = timestamp
        val maxRetrySleepTime = config.getInt(FlowConfig.PROCESSING_MAX_RETRY_DELAY)
        val sleepTime = (2.0.pow(retryState.retryCount - 1.toDouble())) * RETRY_INITIAL_DELAY_MS
        setFlowSleepDuration(min(maxRetrySleepTime, sleepTime.toInt()))
        state.retryState = retryState
    }

    fun markRetrySuccess() {
        state.retryState = null
    }

    fun populateCpkFileHashes(cpkFileHashes: Set<SecureHash>) {
        if (state.cpkFileHashes.isNullOrEmpty()) {
            state.cpkFileHashes = cpkFileHashes.map { net.corda.data.crypto.SecureHash(it.algorithm, ByteBuffer.wrap(it.bytes)) }
        } else {
            throw IllegalStateException("cpk file hash list ${state.cpkFileHashes} cannot be updated to $cpkFileHashes once set")
        }
    }

    fun clearCpkFileHashes() {
        state.cpkFileHashes.clear()
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

    fun setFlowSleepDuration(sleepTimeMs: Int) {
        state.maxFlowSleepDuration = min(sleepTimeMs, state.maxFlowSleepDuration)
    }

    fun toAvro(): PipelineState {
        return state
    }

    private fun createAvroExceptionEnvelope(exception: Exception): ExceptionEnvelope {
        return ExceptionEnvelope().apply {
            errorType = FlowProcessingExceptionTypes.FLOW_TRANSIENT_EXCEPTION
            errorMessage = exception.message
        }
    }
}