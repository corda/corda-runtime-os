package net.corda.flow.pipeline.metrics

interface FlowMetrics {
    fun flowEventReceived(flowEventType: String)

    fun flowStarted()

    fun flowFiberEntered()

    fun flowFiberExited()

    fun flowFiberExitedWithSuspension(suspensionOperationName: String?)

    fun flowEventCompleted(flowEventType: String)

    fun flowCompletedSuccessfully()

    fun flowFailed()

    fun flowSessionMessageOutgoing(flowEventType: String)

    fun flowSessionMessageIncoming(flowEventType: String)
}