package net.corda.ledger.libs.uniqueness

import net.corda.ledger.libs.uniqueness.data.UniquenessHoldingIdentity
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import java.time.Duration

interface UniquenessCheckerMetricsFactory {
    fun recordBatchSize(size: Int)
    fun recordBatchExecutionTime(executionTime: Duration)

    fun recordSubBatchExecutionTime(executionTime: Duration, holdingIdentity: UniquenessHoldingIdentity)
    fun recordSubBatchSize(size: Int, holdingIdentity: UniquenessHoldingIdentity)

    fun incrementSuccessfulRequestCount(holdingIdentity: UniquenessHoldingIdentity, result: UniquenessCheckResult, isDuplicate: Boolean)
    fun incrementUnhandledErrorRequestCount(holdingIdentity: UniquenessHoldingIdentity, exception: Exception)
}
