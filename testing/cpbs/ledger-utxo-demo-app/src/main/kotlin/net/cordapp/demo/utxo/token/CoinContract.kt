package net.cordapp.demo.utxo.token

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class CoinContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
    }


}


