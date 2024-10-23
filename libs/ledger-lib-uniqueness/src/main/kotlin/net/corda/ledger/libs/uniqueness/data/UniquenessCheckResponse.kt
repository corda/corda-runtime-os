package net.corda.ledger.libs.uniqueness.data

import net.corda.v5.application.uniqueness.model.UniquenessCheckResult

data class UniquenessCheckResponse(
    val transactionId: String,
    val uniquenessCheckResult: UniquenessCheckResult
)
