package net.corda.ledger.libs.uniqueness.backingstore

import net.corda.virtualnode.HoldingIdentity
import java.time.Duration

interface BackingStoreMetricsFactory {
    fun recordSessionExecutionTime(executionTime: Duration, holdingIdentity: HoldingIdentity)
    fun recordTransactionExecutionTime(executionTime: Duration, holdingIdentity: HoldingIdentity)

    fun recordTransactionAttempts(attempts: Int, holdingIdentity: HoldingIdentity)
    fun incrementTransactionErrorCount(exception: Exception, holdingIdentity: HoldingIdentity)

    fun recordDatabaseReadTime(readTime: Duration, holdingIdentity: HoldingIdentity)
    fun recordDatabaseCommitTime(commitTime: Duration, holdingIdentity: HoldingIdentity)
}
