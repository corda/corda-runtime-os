package net.corda.flow.state.impl

import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig

interface FlowCheckpointFactory {
    fun create(flowId: String, checkpoint: Checkpoint?, config: SmartConfig): FlowCheckpoint
}