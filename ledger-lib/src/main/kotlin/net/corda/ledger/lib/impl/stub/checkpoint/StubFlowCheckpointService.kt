package net.corda.ledger.lib.impl.stub.checkpoint

import net.corda.flow.application.services.FlowCheckpointService
import net.corda.flow.state.FlowCheckpoint

class StubFlowCheckpointService : FlowCheckpointService {
    override fun getCheckpoint(): FlowCheckpoint {
        return StubFlowCheckpoint()
    }
}