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

    fun flowSessionMessageSent(flowEventType: String, sessionId: String, sequenceNumber: Long)

    fun flowSessionMessageReceived(flowEventType: String)

    fun flowSessionMessageReplayed(flowEventType: String, sessionId: String, sequenceNumber: Long)
}