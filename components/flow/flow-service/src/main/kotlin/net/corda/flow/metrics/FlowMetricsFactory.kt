package net.corda.flow.metrics

import net.corda.flow.pipeline.metrics.FlowMetrics
import net.corda.flow.state.FlowCheckpoint

interface FlowMetricsFactory {
    fun create(eventRecordTimestamp: Long, flowCheckpoint: FlowCheckpoint): FlowMetrics
}

