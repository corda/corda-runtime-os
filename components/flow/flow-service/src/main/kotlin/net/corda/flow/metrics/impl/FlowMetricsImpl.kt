package net.corda.flow.metrics.impl

import net.corda.data.flow.output.FlowStates
import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.state.FlowCheckpoint
import net.corda.utilities.time.Clock

@Suppress("TooManyFunctions")
class FlowMetricsImpl(
    private val clock: Clock,
    private val flowMetricsRecorder: FlowMetricsRecorder,
    private val flowCheckpoint: FlowCheckpoint,
    private val recordTimestamp: Long
) : FlowMetrics {

    private val currentState: FlowMetricState
    private val eventReceivedTimestampMillis: Long
    private var fiberStartTime = clock.nowInMillis()
    private var fiberExecutionTime: Long = 0

    init {
        eventReceivedTimestampMillis = clock.nowInMillis()

        currentState = flowCheckpoint.readCustomState(FlowMetricState::class.java) ?: FlowMetricState()
    }

    private val currentFlowStackItemMetricState: FlowStackItemMetricState get() = currentState
        .flowStackItemMetricStates.last()

    override fun flowEventReceived(flowEventType: String) {
        // Record the event lag
        flowMetricsRecorder.recordFlowEventLag(eventReceivedTimestampMillis - recordTimestamp, flowEventType)
    }

    override fun flowStarted() {
        currentState.flowProcessingStartTime = clock.nowInMillis()

        flowCheckpoint.flowStartContext.createdTimestamp?.let {
            flowMetricsRecorder.recordFlowStartLag(clock.nowInMillis() - it.toEpochMilli())
        }
    }

    override fun flowFiberEntered() {
        fiberStartTime = clock.nowInMillis()
        // If we were waiting on a suspension then record the wait time.
        currentState.flowStackItemMetricStates.lastOrNull()?.run {
            if (suspensionAction != null && suspensionTimestampMillis != null) {
                val flowSuspensionTime = clock.nowInMillis() - suspensionTimestampMillis!!
                flowMetricsRecorder.recordFlowSuspensionCompletion(
                    name,
                    isSubFlow,
                    suspensionAction!!,
                    flowSuspensionTime
                )
                totalSuspensionTime += flowSuspensionTime
                suspensionAction = null
                suspensionTimestampMillis = null
            }
        }
    }

    override fun flowFiberExited() {
        fiberExecutionTime = clock.nowInMillis() - fiberStartTime
        currentFlowStackItemMetricState.run {
            flowMetricsRecorder.recordFiberExecution(name, isSubFlow, fiberExecutionTime)
            totalFiberExecutionTime += fiberExecutionTime
            suspensionCount++
            suspensionAction = null
            suspensionTimestampMillis = null
        }
    }

    override fun flowFiberExitedWithSuspension(suspensionOperationName: String?) {
        flowFiberExited()
        currentFlowStackItemMetricState.apply {
            suspensionAction = suspensionOperationName
            suspensionTimestampMillis = clock.nowInMillis()
            totalFiberSuspensionCount++
        }
    }

    override fun flowEventCompleted(flowEventType: String) {
        val pipelineExecutionTime = clock.instant().toEpochMilli() - eventReceivedTimestampMillis
        currentFlowStackItemMetricState.run {
            flowMetricsRecorder.recordPipelineExecution(name, isSubFlow, pipelineExecutionTime, flowEventType)
            totalPipelineExecutionTime += pipelineExecutionTime
            totalEventProcessedCount++
        }

        flowCheckpoint.writeCustomState(currentState)
    }

    override fun flowCompletedSuccessfully() {
        recordFlowCompleted(FlowStates.COMPLETED.toString())
    }

    override fun flowFailed() {
        recordFlowCompleted(FlowStates.FAILED.toString())
    }

    override fun flowSessionMessageSent(flowEventType: String) {
        flowMetricsRecorder.recordFlowSessionMessagesSent(
            currentFlowStackItemMetricState.name,
            isSubFlow = currentState.flowStackItemMetricStates.size > 1,
            flowEventType
        )
    }

    override fun flowSessionMessageReceived(flowEventType: String) {
        // Use the flow context's class name if the subflow stack hasn't been initialised yet.
        val (flowName, isSubFlow) = when (val currentFlowStackItem =
            currentState.flowStackItemMetricStates.lastOrNull()) {
            null -> flowCheckpoint.flowStartContext.flowClassName to false
            else -> currentFlowStackItem.name to currentFlowStackItem.isSubFlow
        }
        flowMetricsRecorder.recordFlowSessionMessagesReceived(flowName, isSubFlow, flowEventType)
    }

    override fun subFlowStarted() {
        val flowStackItemMetricState = FlowStackItemMetricState().apply {
            name = requireNotNull(flowCheckpoint.flowStack.peek()?.flowName) { "Flow stack is empty" }
        }
        if (currentState.flowStackItemMetricStates.isNotEmpty()) {
            flowStackItemMetricState.isSubFlow = true
        }
        currentState.flowStackItemMetricStates.add(flowStackItemMetricState)
        flowStackItemMetricState.flowProcessingStartTime = clock.nowInMillis()
    }

    override fun subFlowFinished(completionStatus: FlowStates) {
        currentState.flowStackItemMetricStates.removeLast().let { finishedFlowStackItem ->

            val currentTime = clock.nowInMillis()
            val flowCompletionTime = currentTime - finishedFlowStackItem.flowProcessingStartTime

            flowMetricsRecorder.recordSubFlowCompletion(
                finishedFlowStackItem.name,
                flowCompletionTime,
                completionStatus.toString()
            )
            flowMetricsRecorder.recordTotalSuspensionTime(
                finishedFlowStackItem.name,
                finishedFlowStackItem.isSubFlow,
                finishedFlowStackItem.totalSuspensionTime
            )
            flowMetricsRecorder.recordTotalFiberExecutionTime(
                finishedFlowStackItem.name,
                finishedFlowStackItem.isSubFlow,
                finishedFlowStackItem.totalFiberExecutionTime
            )
            flowMetricsRecorder.recordTotalPipelineExecutionTime(
                finishedFlowStackItem.name,
                finishedFlowStackItem.isSubFlow,
                finishedFlowStackItem.totalPipelineExecutionTime
            )
            flowMetricsRecorder.recordTotalEventsProcessed(
                finishedFlowStackItem.name,
                finishedFlowStackItem.isSubFlow,
                finishedFlowStackItem.totalEventProcessedCount
            )
            flowMetricsRecorder.recordTotalFiberSuspensions(
                finishedFlowStackItem.name,
                finishedFlowStackItem.isSubFlow,
                finishedFlowStackItem.totalFiberSuspensionCount
            )

            currentFlowStackItemMetricState.apply {
                totalSuspensionTime += finishedFlowStackItem.totalSuspensionTime
                totalFiberExecutionTime += finishedFlowStackItem.totalFiberExecutionTime
                totalPipelineExecutionTime += finishedFlowStackItem.totalPipelineExecutionTime
                totalEventProcessedCount += finishedFlowStackItem.totalEventProcessedCount
                totalFiberSuspensionCount += finishedFlowStackItem.totalFiberSuspensionCount
            }
        }
    }

    private fun recordFlowCompleted(completionStatus: String) {
        val currentTime = clock.nowInMillis()
        val flowCompletionTime = currentTime - currentState.flowProcessingStartTime
        val flowRunTime = currentTime - flowCheckpoint.flowStartContext.createdTimestamp.toEpochMilli()

        currentFlowStackItemMetricState.run {
            flowMetricsRecorder.recordFlowCompletion(name, flowCompletionTime, flowRunTime, completionStatus)
            flowMetricsRecorder.recordTotalSuspensionTime(name, isSubFlow, totalSuspensionTime)
            flowMetricsRecorder.recordTotalFiberExecutionTime(name, isSubFlow, totalFiberExecutionTime)
            flowMetricsRecorder.recordTotalPipelineExecutionTime(name, isSubFlow, totalPipelineExecutionTime)
            flowMetricsRecorder.recordTotalEventsProcessed(name, isSubFlow, totalEventProcessedCount)
            flowMetricsRecorder.recordTotalFiberSuspensions(name, isSubFlow, totalFiberSuspensionCount)
        }
    }

    private fun Clock.nowInMillis(): Long {
        return this.instant().toEpochMilli()
    }

    /**
     * @property flowProcessingStartTime The start time of the top level flow.
     * @property flowStackItemMetricStates Includes all SubFlows as well as the top level flow, after the initial flow
     * stack item has been added. Some metrics reference the flow class name from the flow start context in the scenario
     * that the flow stack for the top level flow has not been added yet.
     */
    private class FlowMetricState {
        var flowProcessingStartTime: Long = 0
        val flowStackItemMetricStates: ArrayList<FlowStackItemMetricState> = arrayListOf()
    }

    private class FlowStackItemMetricState {
        var name: String = ""
        var isSubFlow: Boolean = false
        var flowProcessingStartTime: Long = 0
        var suspensionTimestampMillis: Long? = null
        var suspensionAction: String? = null
        var suspensionCount: Long = 0
        var totalSuspensionTime: Long = 0
        var totalFiberExecutionTime: Long = 0
        var totalPipelineExecutionTime: Long = 0
        var totalEventProcessedCount: Long = 0
        var totalFiberSuspensionCount: Long = 0
    }
}