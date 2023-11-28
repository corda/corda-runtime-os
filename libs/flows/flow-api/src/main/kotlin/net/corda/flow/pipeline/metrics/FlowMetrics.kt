package net.corda.flow.pipeline.metrics

import net.corda.data.flow.output.FlowStates

interface FlowMetrics {
    fun flowEventReceived(flowEventType: String)

    fun flowStarted()

    fun flowFiberEntered()

    fun flowFiberExited()

    fun flowFiberExitedWithSuspension(suspensionOperationName: String?)

    fun flowEventCompleted(flowEventType: String)

    fun flowCompletedSuccessfully()

    fun flowFailed()

    fun flowSessionMessageSent(flowEventType: String)

    fun flowSessionMessageReceived(flowEventType: String)

    fun subFlowStarted()

    fun subFlowFinished(completionStatus: FlowStates)
}
