package net.corda.flow.metrics.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.data.flow.output.FlowStates
import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.state.FlowCheckpoint
import net.corda.utilities.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

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
        val log: Logger = LoggerFactory.getLogger(FlowMetricsImpl::class.java)
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

    override fun subFlowStarted() {
        val subFlowMetricState = SubFlowMetricState().apply {
            name = requireNotNull(flowCheckpoint.flowStack.peek()?.flowName) { "Flow stack is empty" }
        }
        currentState.subFlowMetricStates.add(subFlowMetricState)
        subFlowMetricState.flowProcessingStartTime = clock.nowInMillis()
    }

    override fun subFlowFinished(completionStatus: String) {
//        val flowName = requireNotNull(flowCheckpoint.flowStack.peek()?.flowName) { "Flow stack is empty" }
//        currentState.subFlowMetricStates.add(SubFlowMetricState(flowName))
//        currentState.flowProcessingStartTime = clock.nowInMillis()

        // publish metrics
        // then update the next subflow with the metrics of the now finished subflow

        currentState.subFlowMetricStates.removeLast().let { finishedSubFlow ->

            val currentTime = clock.nowInMillis()
            val flowCompletionTime = currentTime - finishedSubFlow.flowProcessingStartTime
//            val flowRunTime = currentTime - flowCheckpoint.flowStartContext.createdTimestamp.toEpochMilli()

//            flowMetricsRecorder.recordFlowCompletion(flowCompletionTime, flowRunTime, completionStatus)
            flowMetricsRecorder.recordSubFlowCompletion(finishedSubFlow.name, flowCompletionTime, completionStatus)
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
        log.info("CURRENT STATE (flowFiberExitedWithSuspension) = $currentState")
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

    // first session message received means subflow stack isn't constructed yet.
    override fun flowSessionMessageReceived(flowEventType: String) {
        val flowName = currentState.subFlowMetricStates.lastOrNull()?.name ?: flowCheckpoint.flowStartContext.flowClassName
        flowMetricsRecorder.recordFlowSessionMessagesReceived(flowName, flowEventType)
    }

    override fun subFlowFinished(subFlowName: String, subFlowStartTime: Long, completionStatus: String) {
//        val currentTime = System.nanoTime()
        val currentTime = clock.nowInMillis()
        val subFlowRunTime = currentTime - subFlowStartTime
        flowMetricsRecorder.recordSubFlowCompletion(subFlowName, subFlowRunTime, completionStatus)
    }

    private fun recordFlowCompleted(completionStatus: String) {
        val currentTime = clock.nowInMillis()
        val flowCompletionTime = currentTime - currentState.flowProcessingStartTime
        val flowRunTime = currentTime - flowCheckpoint.flowStartContext.createdTimestamp.toEpochMilli()

        // remove the first subflow stack as that is the top level flow and we want to record that one slightly differently
        // then loop through the subflows starting from the end
        // publish each subflow metric
        // also keep a running total of their metric values so that they can be included in the next subflow's metrics
        // can't do it here because i would need to know the stack over the whole flows history, do at subflow finishing time

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

//    private class FlowMetricState {
//        var flowProcessingStartTime: Long = 0
//        var suspensionTimestampMillis: Long? = null
//        var suspensionAction: String? = null
//        var suspensionCount: Long = 0
//        var totalSuspensionTime: Long = 0
//        var totalFiberExecutionTime: Long = 0
//        var totalPipelineExecutionTime: Long = 0
//        var totalEventProcessedCount: Long = 0
//        var totalFiberSuspensionCount: Long = 0
//    }

    private class FlowMetricState {
        var flowProcessingStartTime: Long = 0

        // includes the top level flow as a "subflow"
        // would mean we don't need a split between the flow and subflow metrics anymore because they will be handled in one place
        // could have a tag of "top level flow" to filter out subflows
        val subFlowMetricStates: ArrayList<SubFlowMetricState> = arrayListOf()
        override fun toString(): String {
            return "FlowMetricState(flowProcessingStartTime=$flowProcessingStartTime, subFlowMetricStates=$subFlowMetricStates)"
        }
//        var suspensionTimestampMillis: Long? = null,
//        var suspensionAction: String? = null,
//        var suspensionCount: Long = 0,
//        var totalSuspensionTime: Long = 0,
//        var totalFiberExecutionTime: Long = 0,
//        var totalPipelineExecutionTime: Long = 0,
//        var totalEventProcessedCount: Long = 0,
//        var totalFiberSuspensionCount: Long = 0


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
        override fun toString(): String {
            return "SubFlowMetricState(name='$name', flowProcessingStartTime=$flowProcessingStartTime, suspensionTimestampMillis=$suspensionTimestampMillis, suspensionAction=$suspensionAction, suspensionCount=$suspensionCount, totalSuspensionTime=$totalSuspensionTime, totalFiberExecutionTime=$totalFiberExecutionTime, totalPipelineExecutionTime=$totalPipelineExecutionTime, totalEventProcessedCount=$totalEventProcessedCount, totalFiberSuspensionCount=$totalFiberSuspensionCount)"
        }


    }
}