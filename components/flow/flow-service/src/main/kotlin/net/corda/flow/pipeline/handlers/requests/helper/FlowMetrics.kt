package net.corda.flow.pipeline.handlers.requests.helper

import java.time.Duration
import java.time.Instant
import net.corda.flow.state.FlowCheckpoint
import net.corda.metrics.CordaMetrics

/**
 * Record the following metric:
 * - flow run time
 * - flow result
 * - vnode running the flow
 * - flow class name
 */
fun recordFlowRuntimeMetric(checkpoint: FlowCheckpoint, result: String) {
    val flowStartContext = checkpoint.flowStartContext
    val flowStartTime = flowStartContext.createdTimestamp
    val flowRunningTime = Instant.now().toEpochMilli() - flowStartTime.toEpochMilli()
    CordaMetrics.Metric.FlowRunTime.builder()
        .withTag(CordaMetrics.Tag.VirtualNode, checkpoint.holdingIdentity.shortHash.toString())
        .withTag(CordaMetrics.Tag.FlowClass, flowStartContext.flowClassName)
        .withTag(CordaMetrics.Tag.OperationStatus, result)
        .build().record(Duration.ofMillis(flowRunningTime))
}