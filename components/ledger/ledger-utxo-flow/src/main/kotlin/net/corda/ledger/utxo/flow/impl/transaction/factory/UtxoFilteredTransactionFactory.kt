package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.utxo.flow.impl.transaction.UtxoFilteredTransactionBuilderInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction

interface UtxoFilteredTransactionFactory {

    @Suspendable
    fun create(
        signedTransaction: UtxoSignedTransactionInternal,
        filteredTransactionBuilder: UtxoFilteredTransactionBuilderInternal
    ): UtxoFilteredTransaction
}