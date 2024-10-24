package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * [UtxoSignedLedgerTransaction] is a wrapper that combines the functionality of [UtxoLedgerTransactionInternal] and
 * [UtxoSignedTransactionInternal] for convenience.
 */
interface UtxoSignedLedgerTransaction : UtxoLedgerTransactionInternal, UtxoSignedTransactionInternal {

    /**
     * Gets the delegate [UtxoLedgerTransaction] from the [UtxoSignedLedgerTransaction] instance.
     */
    val ledgerTransaction: UtxoLedgerTransaction

    /**
     * Gets the delegate [UtxoSignedTransaction] from the [UtxoSignedLedgerTransaction] instance.
     */
    val signedTransaction: UtxoSignedTransaction
}
