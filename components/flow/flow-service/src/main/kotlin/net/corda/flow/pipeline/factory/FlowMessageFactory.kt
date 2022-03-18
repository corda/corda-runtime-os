package net.corda.flow.pipeline.factory

import net.corda.data.flow.output.FlowStatus
import net.corda.flow.state.FlowCheckpoint

interface FlowMessageFactory {

    fun createFlowCompleteStatusMessage(checkpoint: FlowCheckpoint, flowResult: String?): FlowStatus

    fun createFlowStartedStatusMessage(checkpoint: FlowCheckpoint): FlowStatus

    fun createFlowFailedStatusMessage(checkpoint: FlowCheckpoint, errorType: String, message: String): FlowStatus
}