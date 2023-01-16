package net.corda.flow.pipeline.factory

import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.state.FlowCheckpoint

/**
 * The [FlowMessageFactory] is responsible for creating instances messages used by the flow engine.
 */
interface FlowMessageFactory {

    /**
     * Creates [FlowStatus] message with a [FlowStates.COMPLETED] status
     *
     * @param checkpoint of the flow being completed.
     * @param flowResult optional flow result string.
     * @return a new instance of a [FlowStatus] record.
     */
    fun createFlowCompleteStatusMessage(checkpoint: FlowCheckpoint, flowResult: String?): FlowStatus

    /**
     * Creates [FlowStatus] message with a [FlowStates.RUNNING] status
     *
     * @param checkpoint of the flow being started.
     * @return a new instance of a [FlowStatus] record.
     */
    fun createFlowStartedStatusMessage(checkpoint: FlowCheckpoint): FlowStatus

    /**
     * Creates [FlowStatus] message with a [FlowStates.RETRYING] status
     *
     * @param checkpoint of the flow being started.
     * @return a new instance of a [FlowStatus] record.
     */
    fun createFlowRetryingStatusMessage(checkpoint: FlowCheckpoint): FlowStatus

    /**
     * Creates [FlowStatus] message with a [FlowStates.FAILED] status
     *
     * @param checkpoint of the flow that failed.
     * @param errorType description of the type/category of error.
     * @param message detailed description of the error and its cause.
     * @return a new instance of a [FlowStatus] record.
     */
    fun createFlowFailedStatusMessage(checkpoint: FlowCheckpoint, errorType: String, message: String): FlowStatus

    /**
     * Creates [FlowStatus] message with a [FlowStates.KILLED] status.
     *
     * @param checkpoint of the flow
     * @param details additional details why the flow was killed.
     * @return a new instance of a [FlowStatus] record.
     */
    fun createFlowKilledStatusMessage(checkpoint: FlowCheckpoint, details: Map<String, String>?): FlowStatus
}