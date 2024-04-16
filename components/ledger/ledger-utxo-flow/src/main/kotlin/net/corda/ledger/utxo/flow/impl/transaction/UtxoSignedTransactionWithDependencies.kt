package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures

class UtxoSignedTransactionWithDependencies(
    val delegate: UtxoSignedTransaction,
    val filteredDependencies: List<UtxoFilteredTransactionAndSignatures>
) : UtxoSignedTransaction by delegate
