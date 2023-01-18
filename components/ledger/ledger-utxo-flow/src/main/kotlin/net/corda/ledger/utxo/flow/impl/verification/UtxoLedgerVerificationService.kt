package net.corda.ledger.utxo.flow.impl.verification

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * [UtxoLedgerVerificationService] verifies contracts of UTXO ledger transactions.
 */
interface UtxoLedgerVerificationService {
    /**
     * Verifies contracts of [UtxoLedgerTransaction].
     *
     * @param transaction UTXO ledger transaction.
     *
     * @return Contracts verification result.
     */
    @Suspendable
    fun verifyContracts(transaction: UtxoLedgerTransaction): Boolean
}