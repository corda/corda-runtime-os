package net.cordapp.demo.utxo.contract

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class TestContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }
}