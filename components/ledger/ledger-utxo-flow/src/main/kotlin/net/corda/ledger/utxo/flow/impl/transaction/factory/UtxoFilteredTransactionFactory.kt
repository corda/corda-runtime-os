package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction

interface UtxoFilteredTransactionFactory {
    @Suspendable
    fun create(
        filteredTransaction: FilteredTransaction
    ) : UtxoFilteredTransaction
}