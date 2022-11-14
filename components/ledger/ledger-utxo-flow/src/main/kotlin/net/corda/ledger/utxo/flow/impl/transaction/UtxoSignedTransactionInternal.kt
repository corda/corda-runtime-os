package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

interface UtxoSignedTransactionInternal: UtxoSignedTransaction {
    val wireTransaction: WireTransaction
}
