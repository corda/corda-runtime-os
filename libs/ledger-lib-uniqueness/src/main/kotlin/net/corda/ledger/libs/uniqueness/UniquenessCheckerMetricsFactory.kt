package net.corda.ledger.libs.uniqueness

import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.virtualnode.HoldingIdentity
import java.lang.Exception
import java.time.Duration

interface UniquenessCheckerMetricsFactory {
    fun recordBatchSize(size: Int)
    fun recordBatchExecutionTime(executionTime: Duration)

    fun recordSubBatchExecutionTime(executionTime: Duration, holdingIdentity: HoldingIdentity)
    fun recordSubBatchSize(size: Int, holdingIdentity: HoldingIdentity)

    fun incrementSuccessfulRequestCount(holdingIdentity: HoldingIdentity, result: UniquenessCheckResult, isDuplicate: Boolean)
    fun incrementUnhandledErrorRequestCount(holdingIdentity: HoldingIdentity, exception: Exception)
}
