package net.corda.flow.pipeline.sessions

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.flow.state.FlowCheckpoint

class CheckpointInitializerImpl : CheckpointInitializer {
    override fun initialize(checkpoint: FlowCheckpoint, startContext: FlowStartContext, waitingFor: WaitingFor) {
        checkpoint.initFlowState(startContext)
        checkpoint.waitingFor = WaitingFor(WaitingForStartFlow)

        checkpoint.cpks
    }
}