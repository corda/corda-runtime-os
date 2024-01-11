package net.corda.flow.metrics.impl

import net.corda.flow.metrics.FlowMetricsRecorder
import net.corda.flow.state.FlowCheckpoint
import net.corda.metrics.CordaMetrics
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("TooManyFunctions")
class FlowMetricsRecorderImpl(
    private val flowCheckpoint: FlowCheckpoint
) : FlowMetricsRecorder {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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

    override fun recordFlowSuspensionCompletion(flowName: String, isSubFlow:Boolean, operationName: String, executionTimeMilli: Long) {
        CordaMetrics.Metric.FlowEventSuspensionWaitTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .withTag(CordaMetrics.Tag.FlowSuspensionAction, operationName)
            .build().record(Duration.ofMillis(executionTimeMilli))
    }

    override fun recordFiberExecution(flowName: String, isSubFlow:Boolean, executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowEventFiberExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordPipelineExecution(flowName: String, isSubFlow:Boolean, executionTimeMillis: Long, flowEventType: String) {
        CordaMetrics.Metric.FlowEventPipelineExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .withTag(CordaMetrics.Tag.FlowEvent, flowEventType)
            .build().record(Duration.ofMillis(executionTimeMillis))
//        logger.info(
//            "reqId=${flowCheckpoint.flowStartContext.requestId}," +
//            "flowName=$flowName," +
//            "flowId=${flowCheckpoint.flowId}," +
//            "eventType=$flowEventType," +
//            "duration=$executionTimeMillis"
//        )
    }

    override fun recordTotalPipelineExecutionTime(flowName: String, isSubFlow:Boolean, executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowPipelineExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalFiberExecutionTime(flowName: String, isSubFlow:Boolean, executionTimeMillis: Long) {
        CordaMetrics.Metric.FlowFiberExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordTotalSuspensionTime(flowName: String, isSubFlow:Boolean, executionTimeMillis: Long) {
        val flowStartContext = flowCheckpoint.flowStartContext
        CordaMetrics.Metric.FlowSuspensionWaitTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowStartContext.flowClassName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .build().record(Duration.ofMillis(executionTimeMillis))
    }

    override fun recordFlowCompletion(flowName: String, executionTimeMillis: Long, runTimeMillis: Long, completionStatus: String) {
        CordaMetrics.Metric.FlowExecutionTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.OperationStatus, completionStatus)
            .build().record(Duration.ofMillis(executionTimeMillis))
        CordaMetrics.Metric.FlowRunTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow = false))
            .withTag(CordaMetrics.Tag.OperationStatus, completionStatus)
            .build().record(Duration.ofMillis(runTimeMillis))
    }

    override fun recordFlowSessionMessagesReceived(flowName: String, isSubFlow:Boolean, flowEventType: String) {
        CordaMetrics.Metric.FlowSessionMessagesReceivedCount.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .withTag(CordaMetrics.Tag.FlowEvent, flowEventType)
            .build().increment()

    }

    override fun recordFlowSessionMessagesSent(flowName: String, isSubFlow:Boolean, flowEventType: String) {
        CordaMetrics.Metric.FlowSessionMessagesSentCount.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .withTag(CordaMetrics.Tag.FlowEvent, flowEventType)
            .build().increment()
    }

    override fun recordTotalEventsProcessed(flowName: String, isSubFlow:Boolean, eventsProcessed: Long) {
        CordaMetrics.Metric.FlowEventProcessedCount.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .build().record(eventsProcessed.toDouble())
    }

    override fun recordTotalFiberSuspensions(flowName: String, isSubFlow:Boolean, fiberSuspensions: Long) {
        CordaMetrics.Metric.FlowFiberSuspensionCount.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, flowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow))
            .build().record(fiberSuspensions.toDouble())
    }

    override fun recordSubFlowCompletion(subFlowName: String, runTimeMillis: Long, completionStatus: String) {
        CordaMetrics.Metric.FlowRunTime.builder()
            .forVirtualNode(flowCheckpoint.holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.FlowClass, subFlowName)
            .withTag(CordaMetrics.Tag.FlowType, getFlowType(isSubFlow = true))
            .withTag(CordaMetrics.Tag.OperationStatus, completionStatus)
            .build().record(Duration.ofMillis(runTimeMillis))
    }

    private fun getFlowType(isSubFlow: Boolean): String {
        return if (isSubFlow) "SubFlow" else "TopLevelFlow"
    }
}