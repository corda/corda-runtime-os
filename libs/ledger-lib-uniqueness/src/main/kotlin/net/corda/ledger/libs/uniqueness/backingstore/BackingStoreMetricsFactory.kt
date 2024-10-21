package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import java.time.Duration

interface BackingStoreMetricsFactory {
    fun recordSessionExecutionTime(executionTime: Duration, holdingIdentity: UniquenessHoldingIdentity)
    fun recordTransactionExecutionTime(executionTime: Duration, holdingIdentity: UniquenessHoldingIdentity)

    fun recordTransactionAttempts(attempts: Int, holdingIdentity: UniquenessHoldingIdentity)
    fun incrementTransactionErrorCount(exception: Exception, holdingIdentity: UniquenessHoldingIdentity)

    fun recordDatabaseReadTime(readTime: Duration, holdingIdentity: UniquenessHoldingIdentity)
    fun recordDatabaseCommitTime(commitTime: Duration, holdingIdentity: UniquenessHoldingIdentity)
}
