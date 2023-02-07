package net.corda.flow.state.impl

import net.corda.data.crypto.SecureHash
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.PipelineState
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.FlowConfig
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Suppress("Unused")
@Component(service = [FlowCheckpointFactory::class])
class FlowCheckpointFactoryImpl : FlowCheckpointFactory {
    override fun create(flowId: String, checkpoint: Checkpoint?, config: SmartConfig): FlowCheckpoint {
        val checkpointToUse = checkpoint ?: newCheckpoint(flowId, config)
        return FlowCheckpointImpl(checkpointToUse, config) { Instant.now() }
    }

    private fun newCheckpoint(newFlowId: String, config: SmartConfig) : Checkpoint {
        val newPipelineState = PipelineState.newBuilder().apply {
            retryState = null
            maxFlowSleepDuration = config.getInt(FlowConfig.PROCESSING_MAX_FLOW_SLEEP_DURATION)
            pendingPlatformError = null
            cpks = emptyList<SecureHash>()
        }.build()
        return Checkpoint.newBuilder().apply {
            flowId = newFlowId
            pipelineState = newPipelineState
            flowState = null
        }.build()
    }
}