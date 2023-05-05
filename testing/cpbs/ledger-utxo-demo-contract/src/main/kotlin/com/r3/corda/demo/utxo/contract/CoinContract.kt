package com.r3.corda.demo.utxo.contract

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class CoinContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {

    }

}


