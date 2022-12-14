package net.corda.ledger.utxo.testkit

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class UtxoInvalidContractExample : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
        throw IllegalStateException("broken")
    }
}