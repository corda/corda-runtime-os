package net.corda.libs.statemanager.impl.metrics

import net.corda.metrics.CordaMetrics

class MetricsRecorderImpl : MetricsRecorder {

    override fun <T> recordProcessingTime(operationType: MetricsRecorder.OperationType, block: () -> T): T {
        return CordaMetrics.Metric.StateManger.ExecutionTime.builder()
            .withTag(CordaMetrics.Tag.OperationName, operationType.toString())
            .build()
            .recordCallable {
                block()
            }!!
    }

    override fun recordFailureCount(operationType: MetricsRecorder.OperationType, count: Int) {
        CordaMetrics.Metric.StateManger.FailureCount.builder()
            .withTag(CordaMetrics.Tag.OperationName, operationType.toString())
            .build()
            .increment(count.toDouble())
    }
}
