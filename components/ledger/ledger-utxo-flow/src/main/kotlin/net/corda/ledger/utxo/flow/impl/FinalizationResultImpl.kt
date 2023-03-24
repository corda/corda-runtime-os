package net.corda.ledger.utxo.flow.impl

import net.corda.v5.ledger.utxo.FinalizationResult
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

class FinalizationResultImpl(private val utxoSignedTransaction:UtxoSignedTransaction):FinalizationResult {
    override fun getTransaction(): UtxoSignedTransaction {
        return utxoSignedTransaction
    }
}
