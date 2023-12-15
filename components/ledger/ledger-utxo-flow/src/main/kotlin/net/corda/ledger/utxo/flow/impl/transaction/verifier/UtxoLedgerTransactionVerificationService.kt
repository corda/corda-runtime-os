package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * [UtxoLedgerTransactionVerificationService] verifies UTXO ledger transactions.
 */
interface UtxoLedgerTransactionVerificationService {
    /**
     * Verify UTXO ledger transaction.
     *
     * @param transaction UTXO ledger transaction.
     *
     * @throws [TransactionVerificationException] in case of unsuccessful verification
     */
    @Suspendable
    fun verify(transaction: UtxoLedgerTransaction)
}
