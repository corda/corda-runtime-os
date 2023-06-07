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
    private var sequenceNumberCache = mutableMapOf<String, MutableSet<Long?>>()

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

    override fun flowEventReceived(flowEventType: String){
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
        if (currentState.suspensionAction != null && currentState.suspensionTimestampMillis != null) {
            val flowSuspensionTime = clock.nowInMillis() - currentState.suspensionTimestampMillis!!
            flowMetricsRecorder.recordFlowSuspensionCompletion(currentState.suspensionAction!!, flowSuspensionTime)
            currentState.totalSuspensionTime += flowSuspensionTime
            currentState.suspensionAction = null
            currentState.suspensionTimestampMillis = null
        }
    }

    override fun flowFiberExited() {
        fiberExecutionTime = clock.nowInMillis() - fiberStartTime
        flowMetricsRecorder.recordFiberExecution(fiberExecutionTime)
        currentState.totalFiberExecutionTime += fiberExecutionTime
        currentState.suspensionCount++
        currentState.suspensionAction = null
        currentState.suspensionTimestampMillis = null
    }

    override fun flowFiberExitedWithSuspension(suspensionOperationName: String?) {
        flowFiberExited()
        currentState.suspensionAction = suspensionOperationName
        currentState.suspensionTimestampMillis = clock.nowInMillis()
        currentState.totalFiberSuspensionCount++
    }

    override fun flowEventCompleted(flowEventType: String) {
        val pipelineExecutionTime = clock.instant().toEpochMilli() - eventReceivedTimestampMillis
        flowMetricsRecorder.recordPipelineExecution(pipelineExecutionTime, flowEventType)
        currentState.totalPipelineExecutionTime += pipelineExecutionTime
        currentState.totalEventProcessedCount++
        flowCheckpoint.setMetricsState(objectMapper.writeValueAsString(currentState))
    }

    override fun flowCompletedSuccessfully() {
        recordFlowCompleted(FlowStates.COMPLETED.toString())
    }

    override fun flowFailed() {
        recordFlowCompleted(FlowStates.FAILED.toString())
    }

    override fun flowSessionMessageSent(flowEventType: String, sessionId: String, sequenceNumber: Long?) {
        val sessionMetricState = currentState.sessionMetricStateBySessionId.computeIfAbsent(sessionId) {
            SessionMetricState()
        }
        flowMetricsRecorder.recordFlowSessionMessagesSent(flowEventType)

        when {
            isReplay(sequenceNumber, sessionMetricState) -> {
                flowMetricsRecorder.recordFlowSessionMessagesReplayed(flowEventType)
            }
            isAckOrError(sequenceNumber) -> {
                //ignore
            }
            else -> {
                val cache = sequenceNumberCache.computeIfAbsent(sessionId) {
                    mutableSetOf()
                }
                cache.add(sequenceNumber)
                while(cache.contains(sessionMetricState.highestSeenSequenceNumber + 1)) {
                    sessionMetricState.highestSeenSequenceNumber++
                    cache.remove(sessionMetricState.highestSeenSequenceNumber)
                }
            }
        }
    }

    private fun isAckOrError(sequenceNumber: Long?) : Boolean {
        return (sequenceNumber == null || sequenceNumber == 0L)
    }

    private fun isReplay (sequenceNumber: Long?, sessionMetricState: SessionMetricState) : Boolean{
        return when {
            isAckOrError(sequenceNumber) -> false
            sequenceNumber!! <= sessionMetricState.highestSeenSequenceNumber -> true
            else -> false
        }
    }

    override fun flowSessionMessageReceived(flowEventType: String) {
        flowMetricsRecorder.recordFlowSessionMessagesReceived(flowEventType)
    }

    private fun recordFlowCompleted(completionStatus: String) {
        val currentTime = clock.instant().toEpochMilli()
        val flowCompletionTime = currentTime - currentState.flowProcessingStartTime
        val flowRunTime = currentTime - flowCheckpoint.flowStartContext.createdTimestamp.toEpochMilli()
        flowMetricsRecorder.recordFlowCompletion(flowCompletionTime, flowRunTime, completionStatus)
        flowMetricsRecorder.recordTotalSuspensionTime(currentState.totalSuspensionTime)
        flowMetricsRecorder.recordTotalFiberExecutionTime(currentState.totalFiberExecutionTime)
        flowMetricsRecorder.recordTotalPipelineExecutionTime(currentState.totalPipelineExecutionTime)
        flowMetricsRecorder.recordTotalEventsProcessed(currentState.totalEventProcessedCount)
        flowMetricsRecorder.recordTotalFiberSuspensions(currentState.totalFiberSuspensionCount)
    }

    private fun Clock.nowInMillis(): Long {
        return this.instant().toEpochMilli()
    }

    internal class FlowMetricState {
        var flowProcessingStartTime: Long = 0
        var suspensionTimestampMillis: Long? = null
        var suspensionAction: String? = null
        var suspensionCount: Long = 0
        var totalSuspensionTime: Long = 0
        var totalFiberExecutionTime: Long = 0
        var totalPipelineExecutionTime: Long = 0
        var totalEventProcessedCount: Long = 0
        var totalFiberSuspensionCount: Long = 0
        var sessionMetricStateBySessionId: MutableMap<String, SessionMetricState> = mutableMapOf()
    }

    internal class SessionMetricState {
        var highestSeenSequenceNumber: Long = 0
    }
}