package net.corda.flow.manager.factory

import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.Checkpoint

interface FlowMessageFactory {

    fun createFlowCompleteStatusMessage(checkpoint: Checkpoint, flowResult: String?): FlowStatus

    fun createFlowStartedStatusMessage(checkpoint: Checkpoint): FlowStatus

    fun createFlowFailedStatusMessage(checkpoint: Checkpoint, errorType: String, message: String): FlowStatus
}