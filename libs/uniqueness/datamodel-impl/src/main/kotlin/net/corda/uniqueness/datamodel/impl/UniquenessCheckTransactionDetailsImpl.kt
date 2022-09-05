package net.corda.uniqueness.datamodel.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckResult
import net.corda.v5.ledger.utxo.uniqueness.model.UniquenessCheckTransactionDetails

data class UniquenessCheckTransactionDetailsImpl(
    override val txId: SecureHash,
    override val result: UniquenessCheckResult
) : UniquenessCheckTransactionDetails