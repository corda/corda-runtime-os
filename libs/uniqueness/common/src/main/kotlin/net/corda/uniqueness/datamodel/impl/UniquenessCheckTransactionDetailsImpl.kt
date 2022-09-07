package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckTransactionDetails
import net.corda.v5.crypto.SecureHash

data class UniquenessCheckTransactionDetailsImpl(
    override val txId: SecureHash,
    override val result: UniquenessCheckResult
) : UniquenessCheckTransactionDetails