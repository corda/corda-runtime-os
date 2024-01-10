package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

interface UtxoLedgerTransactionInternal: UtxoLedgerTransaction {
    val wireTransaction: WireTransaction
}