package net.corda.uniqueness.backingstore.impl.osgi

import net.corda.ledger.libs.uniqueness.backingstore.BackingStoreMetricsFactory
import net.corda.metrics.CordaMetrics
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Duration

@Component(service = [BackingStoreMetricsFactory::class])
class JPABackingStoreOsgiMetricsFactory @Activate constructor() : BackingStoreMetricsFactory {

    override fun recordSessionExecutionTime(executionTime: Duration, holdingIdentity: HoldingIdentity) {
        CordaMetrics.Metric.UniquenessBackingStoreSessionExecutionTime
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .build()
            .record(executionTime)
    }

    override fun recordTransactionExecutionTime(executionTime: Duration, holdingIdentity: HoldingIdentity) {
        CordaMetrics.Metric.UniquenessBackingStoreTransactionExecutionTime
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .build()
            .record(executionTime)
    }

    override fun recordTransactionAttempts(attempts: Int, holdingIdentity: HoldingIdentity) {
        CordaMetrics.Metric.UniquenessBackingStoreTransactionAttempts
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .build()
            .record(attempts.toDouble())
    }

    override fun incrementTransactionErrorCount(exception: Exception, holdingIdentity: HoldingIdentity) {
        CordaMetrics.Metric.UniquenessBackingStoreTransactionErrorCount
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.ErrorType, exception.javaClass.simpleName)
            .build()
            .increment()
    }

    override fun recordDatabaseReadTime(readTime: Duration, holdingIdentity: HoldingIdentity) {
        CordaMetrics.Metric.UniquenessBackingStoreDbReadTime
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .withTag(CordaMetrics.Tag.OperationName, "getStateDetails")
            .build()
            .record(readTime)
    }

    override fun recordDatabaseCommitTime(commitTime: Duration, holdingIdentity: HoldingIdentity) {
        CordaMetrics.Metric.UniquenessBackingStoreDbCommitTime
            .builder()
            .withTag(CordaMetrics.Tag.SourceVirtualNode, holdingIdentity.shortHash.toString())
            .build()
            .record(commitTime)
    }
}
