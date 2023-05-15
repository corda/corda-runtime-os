package net.corda.flow.pipeline.metrics

interface FlowMetrics {
    fun flowEventReceived()

    fun flowStarted()

    fun flowFiberEntered()

    fun flowFiberExited()

    fun flowFiberExitedWithSuspension(suspensionOperationName: String?)

    fun flowEventCompleted()

    fun flowCompletedSuccessfully()

    fun flowFailed()
}