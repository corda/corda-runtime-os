package net.corda.flow.state

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.libs.configuration.SmartConfig

interface FlowCheckpointFactory {
    fun create(flowId: String, checkpoint: Checkpoint?, config: SmartConfig): FlowCheckpoint
}