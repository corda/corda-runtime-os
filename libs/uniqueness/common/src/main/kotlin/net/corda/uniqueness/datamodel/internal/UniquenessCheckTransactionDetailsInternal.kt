package net.corda.uniqueness.datamodel.internal

import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.crypto.SecureHash

data class UniquenessCheckTransactionDetailsInternal(
    val txId: SecureHash,
    val result: UniquenessCheckResult
)
