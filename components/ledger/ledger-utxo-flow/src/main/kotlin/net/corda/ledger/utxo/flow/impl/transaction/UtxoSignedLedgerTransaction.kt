package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

/**
 * [UtxoSignedLedgerTransaction] is a wrapper that combines the functionality of [UtxoLedgerTransactionInternal] and
 * [UtxoSignedTransactionInternal] for convenience.
 */
interface UtxoSignedLedgerTransaction : UtxoLedgerTransactionInternal, UtxoSignedTransactionInternal {

    /**
     * Gets the delegate [UtxoLedgerTransaction] from the [UtxoSignedLedgerTransaction] instance.
     */
    val ledgerTransaction: UtxoLedgerTransaction
}