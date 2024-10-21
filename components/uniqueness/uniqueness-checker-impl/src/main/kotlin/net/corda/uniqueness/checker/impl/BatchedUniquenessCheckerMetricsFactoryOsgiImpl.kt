package net.corda.uniqueness.checker.impl

import net.corda.ledger.libs.uniqueness.UniquenessCheckerMetricsFactory
import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.metrics.CordaMetrics
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Duration

@Component(service = [UniquenessCheckerMetricsFactory::class])
class BatchedUniquenessCheckerMetricsFactoryOsgiImpl @Activate constructor(): UniquenessCheckerMetricsFactory {

    private companion object {
        private const val UNHANDLED_EXCEPTION = "UniquenessCheckResultUnhandledException"
    }

    override fun recordBatchSize(size: Int) {
        CordaMetrics.Metric.UniquenessCheckerBatchSize
            .builder()
            .build()
            .record(size.toDouble())
    }

    override fun recordBatchExecutionTime(executionTime: Duration) {
        CordaMetrics.Metric.UniquenessCheckerBatchExecutionTime
            .builder()
            .build()
            .record(executionTime)
    }

    override fun recordSubBatchExecutionTime(executionTime: Duration, holdingIdentity: UniquenessHoldingIdentity) {
        CordaMetrics.Metric.UniquenessCheckerSubBatchExecutionTime
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .build()
            .record(executionTime)
    }

    override fun recordSubBatchSize(size: Int, holdingIdentity: UniquenessHoldingIdentity) {
        CordaMetrics.Metric.UniquenessCheckerSubBatchSize
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .build()
            .record(size.toDouble())
    }

    override fun incrementSuccessfulRequestCount(
        holdingIdentity: UniquenessHoldingIdentity,
        result: UniquenessCheckResult,
        isDuplicate: Boolean
    ) {
        CordaMetrics.Metric.UniquenessCheckerRequestCount
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .withTag(
                CordaMetrics.Tag.ResultType,
                if (UniquenessCheckResultFailure::class.java.isAssignableFrom(result.javaClass)) {
                    (result as UniquenessCheckResultFailure).error.javaClass.simpleName
                } else {
                    result.javaClass.simpleName
                }
            )
            .withTag(CordaMetrics.Tag.IsDuplicate, isDuplicate.toString())
            .build()
            .increment()
    }

    override fun incrementUnhandledErrorRequestCount(holdingIdentity: UniquenessHoldingIdentity, exception: Exception) {
        CordaMetrics.Metric.UniquenessCheckerRequestCount
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.ResultType, UNHANDLED_EXCEPTION)
            .withTag(CordaMetrics.Tag.ErrorType, exception::class.java.simpleName)
            .build()
            .increment()
    }
}
