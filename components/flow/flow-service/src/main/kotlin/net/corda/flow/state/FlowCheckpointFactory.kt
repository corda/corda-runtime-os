package net.corda.flow.state

import net.corda.data.flow.state.Checkpoint
import net.corda.libs.configuration.SmartConfig

interface FlowCheckpointFactory {
    fun create(checkpoint: Checkpoint?, config: SmartConfig): FlowCheckpoint
}