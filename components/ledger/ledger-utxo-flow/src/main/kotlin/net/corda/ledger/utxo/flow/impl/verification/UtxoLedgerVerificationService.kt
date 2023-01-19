package net.corda.ledger.utxo.flow.impl.verification

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.ContractVerificationException
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
     * @throws [ContractVerificationException] if verification was unsuccessful.
     */
    @Suspendable
    fun verifyContracts(transaction: UtxoLedgerTransaction)
}