package net.corda.flow.pipeline

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.state.FlowCheckpoint

interface CheckpointInitializer {
    fun initialize(checkpoint: FlowCheckpoint, startContext: FlowStartContext, waitingFor: WaitingFor)
}