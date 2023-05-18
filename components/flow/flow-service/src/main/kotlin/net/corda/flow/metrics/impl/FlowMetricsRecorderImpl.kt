package net.corda.flow.metrics.impl

import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.state.FlowCheckpoint
import net.corda.metrics.CordaMetrics
import java.time.Duration

class FlowMetricsRecorderImpl(
    private val flowCheckpoint: FlowCheckpoint
) : FlowMetricsRecorder {

    override fun recordFlowEventLag(lagMilli: Long, flowEventType: String) {
        CordaMetrics.Metric.FlowEventLagTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.FlowEvent, flowEventType)
            .build().record(Duration.ofMillis(lagMilli))
    }

    override fun recordFlowStartLag(lagMilli: Long) {
        CordaMetrics.Metric.FlowStartLag.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(lagMilli))
    }

    override fun recordFlowSuspensionCompletion(operationName: String, executionTimeMilli: Long) {
        CordaMetrics.Metric.FlowEventSuspensionWaitTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.FlowSuspensionAction, operationName)
            .build().record(Duration.ofMillis(executionTimeMilli))
    }

    override fun recordFiberExecution(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowEventFiberExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordPipelineExecution(executionTimeMillis: Long, flowEventType: String) {
        CordaMetrics.Metric.FlowEventPipelineExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.FlowEvent, flowEventType)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalPipelineExecutionTime(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowPipelineExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalFiberExecutionTime(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowFiberExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalSuspensionTime(executionTimeMillis: Long) {
        val flowStartContext = flowCheckpoint.flowStartContext
        CordaMetrics.Metric.FlowSuspensionWaitTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordFlowCompletion(executionTimeMillis: Long, completionStatus: String) {
        CordaMetrics.Metric.FlowExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.OperationStatus, completionStatus)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }
}