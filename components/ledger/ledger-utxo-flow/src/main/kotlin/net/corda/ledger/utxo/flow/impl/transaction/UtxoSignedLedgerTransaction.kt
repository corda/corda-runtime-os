package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal

interface UtxoSignedLedgerTransaction : UtxoSignedTransactionInternal, UtxoLedgerTransactionInternal {

    val ledgerTransaction: UtxoLedgerTransactionInternal
}