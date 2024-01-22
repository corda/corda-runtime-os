package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.filtered.FilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction

interface UtxoFilteredTransactionInternal : UtxoFilteredTransaction {

    val filteredTransaction: FilteredTransaction
}
