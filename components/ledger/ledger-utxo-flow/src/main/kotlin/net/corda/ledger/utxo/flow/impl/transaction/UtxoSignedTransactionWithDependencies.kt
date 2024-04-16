package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures

class UtxoSignedTransactionWithDependencies(
    private val delegate: UtxoSignedTransactionInternal,
    val filteredDependencies: List<UtxoFilteredTransactionAndSignatures>
) : UtxoSignedTransactionInternal by delegate
