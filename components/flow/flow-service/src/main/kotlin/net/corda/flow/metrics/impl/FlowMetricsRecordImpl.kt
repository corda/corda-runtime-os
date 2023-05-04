package net.corda.flow.metrics.impl

import net.corda.flow.metrics.FlowMetricsRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.metrics.CordaMetrics
import java.time.Duration

class FlowMetricsRecordImpl(
    private val flowCheckpoint: FlowCheckpoint
) : FlowMetricsRecord {

    override fun recordFlowEventLag(lagMilli: Long) {
        CordaMetrics.Metric.FlowEventLagTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(lagMilli))
    }

    override fun recordFlowStartLag(lagMilli: Long) {
        CordaMetrics.Metric.FlowStartLag.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(lagMilli))
    }

    override fun recordFlowSuspensionCompletion(operationName: String, executionTimeMilli: Long) {
        CordaMetrics.Metric.FlowEventSuspensionWaitTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.FlowSuspensionAction, operationName)
            .build().record(Duration.ofMillis(executionTimeMilli))
        CordaMetrics.Metric.FlowSuspensionWaitCount.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.FlowSuspensionAction, operationName)
            .build().increment()
    }

    override fun recordFiberExecution(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowEventFiberExecutionTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
        CordaMetrics.Metric.FlowEventFiberExecutionCount.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().increment()
    }

    override fun recordPipelineExecution(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowEventPipelineExecutionTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
        CordaMetrics.Metric.FlowEventPipelineExecutionCount.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().increment()
    }

    override fun recordTotalPipelineExecutionTime(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowPipelineExecutionTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalFiberExecutionTime(executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowFiberExecutionTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalSuspensionTime(executionTimeMillis: Long) {
        val flowStartContext = flowCheckpoint.flowStartContext
        CordaMetrics.Metric.FlowSuspensionWaitTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowStartContext.flowClassName)
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordFlowCompletion(executionTimeMillis: Long, completionStatus: String) {
        val flowStartContext = flowCheckpoint.flowStartContext
        CordaMetrics.Metric.FlowExecutionTime.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowCheckpoint.flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.OperationStatus, completionStatus)
            .build().record(Duration.ofMillis(executionTimeMillis))
        CordaMetrics.Metric.FlowExecutionCount.builder()
            .withTag(CordaMetrics.Tag.VirtualNode, flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.OperationStatus, completionStatus)
            .build().increment()
    }
}