package net.corda.flow.pipeline.metrics

import java.time.Instant

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

    fun subFlowFinished(subFlowName: String, subFlowStartTime: Long, completionStatus: String)
    fun subFlowStarted()
    fun subFlowFinished(completionStatus: String)
}