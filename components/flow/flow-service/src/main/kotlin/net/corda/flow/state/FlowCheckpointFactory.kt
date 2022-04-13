package net.corda.flow.state

import net.corda.data.flow.state.Checkpoint

interface FlowCheckpointFactory {
    fun create(checkpoint: Checkpoint?): FlowCheckpoint
}