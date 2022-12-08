package net.corda.ledger.utxo.flow.impl.transaction.filtered.factory

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderInternal
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransactionBuilder

/**
 * [UtxoFilteredTransactionFactory] creates [UtxoFilteredTransaction]s from [UtxoFilteredTransactionBuilder]s.
 */
interface UtxoFilteredTransactionFactory {

    /**
     * Creates a [UtxoFilteredTransaction].
     *
     * @param signedTransaction The [UtxoSignedTransactionInternal] to filter.
     * @param filteredTransactionBuilder The [UtxoFilteredTransactionBuilder] that specifies the components to keep in the filtered
     * transaction.
     *
     * @return A [UtxoFilteredTransaction].
     */
    @Suspendable
    fun create(
        signedTransaction: UtxoSignedTransactionInternal,
        filteredTransactionBuilder: UtxoFilteredTransactionBuilderInternal
    ): UtxoFilteredTransaction
}