package net.corda.simulator.runtime.ledger.utxo

import net.corda.v5.ledger.utxo.FinalizationResult
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

class SimFinalizationResultImpl(private val utxoSignedTransaction:UtxoSignedTransaction):FinalizationResult {
    override fun getTransaction(): UtxoSignedTransaction {
        return utxoSignedTransaction
    }
}