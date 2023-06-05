package net.corda.flow.metrics.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.data.flow.output.FlowStates
import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.state.FlowCheckpoint
import net.corda.utilities.time.Clock

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

    private companion object {
        val objectMapper = ObjectMapper()
    }

    init {
        eventReceivedTimestampMillis = clock.nowInMillis()

        currentState = objectMapper.readValue(
            flowCheckpoint.flowMetricsState,
            FlowMetricState::class.java
        )
    }

    private val currentSubFlowMetricState: SubFlowMetricState get() = currentState.subFlowMetricStates.last()

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
        currentState.subFlowMetricStates.lastOrNull()?.run {
            if (suspensionAction != null && suspensionTimestampMillis != null) {
                val flowSuspensionTime = clock.nowInMillis() - suspensionTimestampMillis!!
                flowMetricsRecorder.recordFlowSuspensionCompletion(name, suspensionAction!!, flowSuspensionTime)
                totalSuspensionTime += flowSuspensionTime
                suspensionAction = null
                suspensionTimestampMillis = null
            }
        }
    }

    override fun flowFiberExited() {
        fiberExecutionTime = clock.nowInMillis() - fiberStartTime
        currentSubFlowMetricState.run {
            flowMetricsRecorder.recordFiberExecution(name, fiberExecutionTime)
            totalFiberExecutionTime += fiberExecutionTime
            suspensionCount++
            suspensionAction = null
            suspensionTimestampMillis = null
        }
    }

    override fun flowFiberExitedWithSuspension(suspensionOperationName: String?) {
        flowFiberExited()
        currentSubFlowMetricState.apply {
            suspensionAction = suspensionOperationName
            suspensionTimestampMillis = clock.nowInMillis()
            totalFiberSuspensionCount++
        }
    }

    override fun flowEventCompleted(flowEventType: String) {
        val pipelineExecutionTime = clock.instant().toEpochMilli() - eventReceivedTimestampMillis
        currentSubFlowMetricState.run {
            flowMetricsRecorder.recordPipelineExecution(name, pipelineExecutionTime, flowEventType)
            totalPipelineExecutionTime += pipelineExecutionTime
            totalEventProcessedCount++
        }
        flowCheckpoint.setMetricsState(objectMapper.writeValueAsString(currentState))
    }

    override fun flowCompletedSuccessfully() {
        recordFlowCompleted(FlowStates.COMPLETED.toString())
    }

    override fun flowFailed() {
        recordFlowCompleted(FlowStates.FAILED.toString())
    }

    override fun flowSessionMessageSent(flowEventType: String) {
        flowMetricsRecorder.recordFlowSessionMessagesSent(currentSubFlowMetricState.name, flowEventType)
    }

    override fun flowSessionMessageReceived(flowEventType: String) {
        // Use the flow context's class name if the subflow stack hasn't been initialised yet.
        val flowName = currentState.subFlowMetricStates.lastOrNull()?.name ?: flowCheckpoint.flowStartContext.flowClassName
        flowMetricsRecorder.recordFlowSessionMessagesReceived(flowName, flowEventType)
    }

    override fun subFlowStarted() {
        val subFlowMetricState = SubFlowMetricState().apply {
            name = requireNotNull(flowCheckpoint.flowStack.peek()?.flowName) { "Flow stack is empty" }
        }
        currentState.subFlowMetricStates.add(subFlowMetricState)
        subFlowMetricState.flowProcessingStartTime = clock.nowInMillis()
    }

    override fun subFlowFinished(completionStatus: FlowStates) {
        currentState.subFlowMetricStates.removeLast().let { finishedSubFlow ->

            val currentTime = clock.nowInMillis()
            val flowCompletionTime = currentTime - finishedSubFlow.flowProcessingStartTime

            flowMetricsRecorder.recordSubFlowCompletion(finishedSubFlow.name, flowCompletionTime, completionStatus.toString())
            flowMetricsRecorder.recordTotalSuspensionTime(finishedSubFlow.name, finishedSubFlow.totalSuspensionTime)
            flowMetricsRecorder.recordTotalFiberExecutionTime(finishedSubFlow.name, finishedSubFlow.totalFiberExecutionTime)
            flowMetricsRecorder.recordTotalPipelineExecutionTime(finishedSubFlow.name, finishedSubFlow.totalPipelineExecutionTime)
            flowMetricsRecorder.recordTotalEventsProcessed(finishedSubFlow.name, finishedSubFlow.totalEventProcessedCount)
            flowMetricsRecorder.recordTotalFiberSuspensions(finishedSubFlow.name, finishedSubFlow.totalFiberSuspensionCount)

            currentSubFlowMetricState.apply {
                totalSuspensionTime += finishedSubFlow.totalSuspensionTime
                totalFiberExecutionTime += finishedSubFlow.totalFiberExecutionTime
                totalPipelineExecutionTime += finishedSubFlow.totalPipelineExecutionTime
                totalEventProcessedCount += finishedSubFlow.totalEventProcessedCount
                totalFiberSuspensionCount += finishedSubFlow.totalFiberSuspensionCount
            }
        }
    }

    private fun recordFlowCompleted(completionStatus: String) {
        val currentTime = clock.nowInMillis()
        val flowCompletionTime = currentTime - currentState.flowProcessingStartTime
        val flowRunTime = currentTime - flowCheckpoint.flowStartContext.createdTimestamp.toEpochMilli()

        currentSubFlowMetricState.run {
            flowMetricsRecorder.recordFlowCompletion(name, flowCompletionTime, flowRunTime, completionStatus)
            flowMetricsRecorder.recordTotalSuspensionTime(name, totalSuspensionTime)
            flowMetricsRecorder.recordTotalFiberExecutionTime(name, totalFiberExecutionTime)
            flowMetricsRecorder.recordTotalPipelineExecutionTime(name, totalPipelineExecutionTime)
            flowMetricsRecorder.recordTotalEventsProcessed(name, totalEventProcessedCount)
            flowMetricsRecorder.recordTotalFiberSuspensions(name, totalFiberSuspensionCount)
        }
    }

    private fun Clock.nowInMillis(): Long {
        return this.instant().toEpochMilli()
    }

    /**
     * @property flowProcessingStartTime The start time of the top level flow.
     * @property subFlowMetricStates Includes all SubFlows as well as the top level flow, after the initial flow stack item has been added.
     * Some metrics reference the flow class name from the flow start context in the scenario that the flow stack for the top level flow
     * has not been added yet.
     */
    private class FlowMetricState {
        var flowProcessingStartTime: Long = 0
        val subFlowMetricStates: ArrayList<SubFlowMetricState> = arrayListOf()
    }

    private class SubFlowMetricState {
        var name: String = ""
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