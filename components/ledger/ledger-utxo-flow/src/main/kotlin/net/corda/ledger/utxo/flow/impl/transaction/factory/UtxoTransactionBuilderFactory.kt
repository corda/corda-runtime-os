package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

interface UtxoTransactionBuilderFactory {

    fun create(): UtxoTransactionBuilder
}