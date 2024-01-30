package net.corda.flow.state.impl

import net.corda.data.crypto.SecureHash
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.PipelineState
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [FlowCheckpointFactory::class])
class FlowCheckpointFactoryImpl @Activate constructor(
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
) : FlowCheckpointFactory {
    override fun create(flowId: String, checkpoint: Checkpoint?, config: SmartConfig): FlowCheckpoint {
        val checkpointToUse = checkpoint ?: newCheckpoint(flowId)
        return FlowCheckpointImpl(checkpointToUse, config)
    }

    private fun newCheckpoint(newFlowId: String): Checkpoint {
        val newPipelineState = PipelineState.newBuilder().apply {
            pendingPlatformError = null
            cpkFileHashes = emptyList<SecureHash>()
        }.build()
        return Checkpoint.newBuilder().apply {
            flowId = newFlowId
            pipelineState = newPipelineState
            initialPlatformVersion = platformInfoProvider.localWorkerPlatformVersion
            flowState = null
            savedOutputs = emptyList()
        }.build()
    }
}
