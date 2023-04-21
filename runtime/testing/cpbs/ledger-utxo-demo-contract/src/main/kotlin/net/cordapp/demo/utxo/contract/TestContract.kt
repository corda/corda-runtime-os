package net.cordapp.demo.utxo.contract

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class TestContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }

    override fun isVisible(state: ContractState, checker: VisibilityChecker): Boolean {
        return when (state) {
            is TestUtxoState -> state.identifier % 2 == 0
            else -> false
        }
    }
}